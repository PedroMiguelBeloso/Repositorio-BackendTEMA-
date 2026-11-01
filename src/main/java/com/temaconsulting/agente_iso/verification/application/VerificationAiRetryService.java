package com.temaconsulting.agente_iso.verification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.application.port.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultDecisionFailure;
import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityRequest;
import com.temaconsulting.agente_iso.verification.application.model.AiRetryAttempt;
import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.application.port.AiRetryRepository;
import com.temaconsulting.agente_iso.verification.application.port.ApplicabilityAiPort;
import com.temaconsulting.agente_iso.verification.application.port.TicketEvidenceAiPort;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultRepository;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;
import com.temaconsulting.agente_iso.verification.domain.HumanDecisionType;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.text.Normalizer;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Re-reads authorized Redmine facts before asking Gemini for a new proposal. */
@Service
public class VerificationAiRetryService {
    private static final Logger log = LoggerFactory.getLogger(VerificationAiRetryService.class);
    private final VerificationRunRepository runs;
    private final VerificationRunWorkLoader loader;
    private final VerificationResultRepository results;
    private final AiRetryRepository retries;
    private final RedmineTicketReader tickets;
    private final ApplicabilityAiPort applicabilityAi;
    private final TicketEvidenceAiPort ticketAi;
    private final ProgrammedEvidenceCheckService checks;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock;
    private final String provider;
    private final String apiKey;

    public VerificationAiRetryService(VerificationRunRepository runs, VerificationRunWorkLoader loader,
            VerificationResultRepository results, AiRetryRepository retries, RedmineTicketReader tickets,
            ApplicabilityAiPort applicabilityAi, TicketEvidenceAiPort ticketAi,
            ProgrammedEvidenceCheckService checks, Clock clock,
            @Value("${tema.ai.provider:GEMINI}") String provider,
            @Value("${tema.gemini.api-key:}") String apiKey) {
        this.runs = runs;
        this.loader = loader;
        this.results = results;
        this.retries = retries;
        this.tickets = tickets;
        this.applicabilityAi = applicabilityAi;
        this.ticketAi = ticketAi;
        this.checks = checks;
        this.clock = clock;
        this.provider = provider;
        this.apiKey = apiKey;
    }

    public VerificationResult retry(UUID runId, UUID resultId, int revision, UUID actor) {
        if (revision < 1) throw new ApiException(ProblemCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                "La revisión debe ser positiva.", List.of());
        if (!"GEMINI".equalsIgnoreCase(provider) || apiKey.isBlank()) {
            throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini no está configurado para reintentar la interpretación.", List.of());
        }
        var run = runs.findById(runId).orElseThrow(VerificationResultDecisionFailure::notFound);
        var current = results.findByRunAndId(runId, resultId)
                .orElseThrow(VerificationResultDecisionFailure::notFound);
        if (!"COMPLETED".equals(run.status()) || current.revision() != revision) {
            throw VerificationResultDecisionFailure.conflict();
        }
        var work = loader.load(run);
        var item = work.items().stream()
                .filter(candidate -> candidate.expectation().expectationId().equals(current.expectationId()))
                .findFirst().orElseThrow(VerificationResultDecisionFailure::notFound);
        var expectation = item.expectation();
        boolean applicabilityPending = expectation.applicability() == ApplicabilityMode.CASE_DEPENDENT
                && current.applicability().decision() == ApplicabilityDecision.UNDETERMINED;
        boolean ticketEvidence = expectation.registry().evidenceType() == EvidenceType.TICKET;
        if (!applicabilityPending && !ticketEvidence) {
            throw new ApiException(ProblemCode.UNSUPPORTED_SCOPE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Este resultado no tiene una interpretación de Gemini para reintentar.", List.of());
        }
        if (ticketEvidence && current.applicability().decision() == ApplicabilityDecision.NOT_APPLICABLE) {
            throw new ApiException(ProblemCode.UNSUPPORTED_SCOPE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se reinterpreta un ticket declarado no aplicable por el Administrador.", List.of());
        }
        List<TicketSnapshot> snapshots = readTickets(run.redmineProjectId(), run.ticketSelections().groups(),
                expectation.ticketSelectionGroupId(), current.subjectTicketId());
        if (ticketEvidence && snapshots.isEmpty()) {
            throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo releer el ticket de Redmine.", List.of());
        }
        Instant observedAt = clock.instant();
        ApplicabilityAssessment proposedApplicability = current.applicability();
        String model = null;
        String responseId = null;
        if (applicabilityPending) {
            var facts = facts(run.note(), snapshots);
            if (facts.isEmpty()) {
                throw new ApiException(ProblemCode.UNSUPPORTED_SCOPE, HttpStatus.UNPROCESSABLE_ENTITY,
                        "No hay hechos del caso para reintentar la aplicabilidad. Agregá una nota del caso en una nueva verificación o resolvé la aplicabilidad manualmente.",
                        List.of());
            }
            var proposal = applicabilityAi.assess(new AiApplicabilityRequest(
                    expectation.expectationId(), expectation.displayName(), expectation.applicability(),
                    run.serviceTypeSnapshot().name(), work.policies(), item.instructions(), facts));
            if (proposal == null || !expectation.expectationId().equals(proposal.expectationId())) {
                throw new IllegalStateException("Gemini returned an invalid applicability proposal");
            }
            proposedApplicability = new ApplicabilityAssessment(proposal.applicability(),
                    DecisionOrigin.AI_APPLICABILITY, proposal.reason(), proposal.limitations());
            model = proposal.model();
            responseId = proposal.responseId();
        }
        boolean humanApplicability = current.humanDecisions().stream()
                .anyMatch(event -> event.decisionType() == HumanDecisionType.APPLICABILITY);
        boolean humanStatus = current.humanDecisions().stream()
                .anyMatch(event -> event.decisionType() == HumanDecisionType.RESULT_STATUS);
        ApplicabilityAssessment effectiveApplicability = humanApplicability
                ? current.applicability() : proposedApplicability;
        ResultStatus proposedStatus = current.proposedStatus();
        String rationale = current.rationale();
        List<String> limitations = current.limitations();
        List<VerificationResultWriteCommand.EvidenceReference> evidence = current.evidence().stream()
                .map(ref -> new VerificationResultWriteCommand.EvidenceReference(
                        ref.kind(), ref.source(), ref.externalId(), ref.displayName(),
                        ref.webUrl(), ref.observedAt(), ref.ticketId())).toList();
        if (effectiveApplicability.decision() == ApplicabilityDecision.NOT_APPLICABLE) {
            proposedStatus = ResultStatus.NOT_APPLICABLE;
            rationale = "La nueva propuesta de IA indica que esta evidencia no aplica al caso.";
            limitations = List.of();
        } else if (effectiveApplicability.decision() == ApplicabilityDecision.UNDETERMINED) {
            proposedStatus = ResultStatus.REVIEW_REQUIRED;
            rationale = "La aplicabilidad sigue indeterminada con los datos disponibles.";
            limitations = proposedApplicability.limitations();
        } else if (ticketEvidence && !snapshots.isEmpty()) {
            TicketSnapshot ticket = snapshots.getFirst();
            var assessment = ticketAi.assess(expectation.displayName(), expectation.registry().canonicalName(),
                    expectation.registry().officialNomenclature(), work.policies(), item.instructions(), ticket);
            proposedStatus = assessment.status();
            if (proposedStatus == ResultStatus.NOT_APPLICABLE) proposedStatus = ResultStatus.REVIEW_REQUIRED;
            String rule = normalize(expectation.displayName() + " " + expectation.registry().canonicalName());
            if (proposedStatus == ResultStatus.COMPLIES && rule.contains("actualiz")
                    && assessment.citedFields().stream().noneMatch(field ->
                    field.equals("journals") || field.equals("customFields"))) {
                proposedStatus = ResultStatus.REVIEW_REQUIRED;
            }
            if (proposedStatus == ResultStatus.COMPLIES && rule.contains("valid")
                    && assessment.citedFields().stream().noneMatch(field ->
                    field.equals("journals") || field.equals("customFields") || field.equals("description"))) {
                proposedStatus = ResultStatus.REVIEW_REQUIRED;
            }
            if (rule.contains("cerrad") && Boolean.FALSE.equals(ticket.closed())) {
                proposedStatus = ResultStatus.DOES_NOT_COMPLY;
            }
            rationale = "Nueva propuesta de Gemini: " + assessment.rationale();
            limitations = assessment.limitations();
            model = assessment.model() == null ? model : assessment.model();
            responseId = assessment.responseId() == null ? responseId : assessment.responseId();
            evidence = List.of(new VerificationResultWriteCommand.EvidenceReference(
                    EvidenceType.TICKET, IntegrationSource.REDMINE, ticket.id(),
                    ticket.subject() == null ? "Ticket #" + ticket.id() : ticket.subject(),
                    null, observedAt, ticket.id()));
        } else if (!ticketEvidence) {
            var assessment = checks.assess(expectation, work.service(), ApplicabilityDecision.APPLIES,
                    null, work.policies(), item.instructions());
            proposedStatus = assessment.proposedStatus();
            rationale = assessment.rationale();
            limitations = assessment.limitations();
            evidence = assessment.evidenceReferences().stream()
                    .filter(ref -> ref.kind() != null && ref.source() != null && ref.externalId() != null
                            && ref.displayName() != null && ref.observedAt() != null)
                    .map(ref -> new VerificationResultWriteCommand.EvidenceReference(
                            ref.kind(), ref.source(), ref.externalId(), ref.displayName(),
                            ref.webUrl(), ref.observedAt(), ref.ticketId())).toList();
        }
        ResultStatus effectiveStatus = humanStatus ? current.effectiveStatus() : proposedStatus;
        String outcome = humanStatus || humanApplicability ? "HUMAN_PRESERVED"
                : proposedStatus == current.proposedStatus() ? "NO_CHANGE" : "APPLIED";
        String snapshotJson;
        String previousResultJson;
        try {
            snapshotJson = mapper.writeValueAsString(snapshots);
            previousResultJson = mapper.writeValueAsString(current);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the observed ticket snapshot", ex);
        }
        var attempt = new AiRetryAttempt(UUID.randomUUID(), runId, resultId, actor, observedAt,
                snapshotJson, previousResultJson,
                revision, current.proposedStatus(), current.effectiveStatus(), proposedStatus,
                effectiveStatus, effectiveApplicability.decision(), rationale, outcome, model, responseId);
        var updated = retries.save(attempt, effectiveApplicability, limitations, evidence);
        log.info("verification_ai_retry runId={} resultId={} outcome={} proposedStatus={} revision={}",
                runId, resultId, outcome, proposedStatus, updated.revision());
        return updated;
    }

    public List<AiRetryAttempt> history(UUID runId, UUID resultId) {
        results.findByRunAndId(runId, resultId)
                .orElseThrow(VerificationResultDecisionFailure::notFound);
        return retries.list(runId, resultId);
    }

    private List<TicketSnapshot> readTickets(String projectId,
            List<com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelection> groups,
            String groupId, String subjectTicketId) {
        if (projectId == null) return List.of();
        List<String> ids = subjectTicketId != null ? List.of(subjectTicketId)
                : groups.stream().filter(group -> Objects.equals(group.groupId(), groupId))
                .flatMap(group -> group.ticketIds().stream()).distinct().limit(10).toList();
        List<TicketSnapshot> snapshots = new ArrayList<>();
        for (String id : ids) {
            TicketSnapshot snapshot = tickets.getTicket(projectId, id);
            if (snapshot == null || !id.equals(snapshot.id()) || !projectId.equals(snapshot.projectId())) {
                throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                        "Redmine no confirmó la identidad del ticket seleccionado.", List.of());
            }
            snapshots.add(snapshot);
        }
        return List.copyOf(snapshots);
    }

    private static List<AiApplicabilityRequest.Fact> facts(String note, List<TicketSnapshot> snapshots) {
        List<AiApplicabilityRequest.Fact> facts = new ArrayList<>();
        if (note != null && !note.isBlank()) addFact(facts, "run:note", "Nota de la verificación", note);
        for (TicketSnapshot ticket : snapshots) {
            String prefix = "ticket:" + ticket.id() + ":";
            addFact(facts, prefix + "subject", "Asunto", ticket.subject());
            addFact(facts, prefix + "description", "Descripción", ticket.description());
            addFact(facts, prefix + "status", "Estado", ticket.statusName());
            if (ticket.closed() != null) addFact(facts, prefix + "closed", "Cerrado", ticket.closed().toString());
            int i = 0;
            for (var field : ticket.customFields().stream().limit(20).toList()) {
                addFact(facts, prefix + "custom:" + i++, "Campo " + field.name(), String.valueOf(field.value()));
            }
            i = 0;
            for (var entry : ticket.journals().stream().limit(20).toList()) {
                addFact(facts, prefix + "journal:" + i++, "Historial", entry.notes());
            }
        }
        return facts;
    }

    private static void addFact(List<AiApplicabilityRequest.Fact> facts, String reference, String label, String value) {
        if (value != null && !value.isBlank()) {
            facts.add(new AiApplicabilityRequest.Fact(reference, label,
                    value.length() > 2000 ? value.substring(0, 2000) : value));
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
