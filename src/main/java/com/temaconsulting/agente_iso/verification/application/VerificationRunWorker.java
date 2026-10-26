package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityRequest;
import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.model.EvaluationContext;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultPort;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultProgress;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.application.port.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.ExpectationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One bounded executor processes runs; each result is committed independently by TC-176. */
@Service
public class VerificationRunWorker {
    private static final Logger log = LoggerFactory.getLogger(VerificationRunWorker.class);
    private final VerificationRunRepository runs;
    private final VerificationRunWorkLoader loader;
    private final ApplicabilityPolicy applicability;
    private final ProgrammedEvidenceCheckService checks;
    private final VerificationResultPort results;
    private final RedmineTicketReader tickets;

    public VerificationRunWorker(VerificationRunRepository runs, VerificationRunWorkLoader loader,
                                 ApplicabilityPolicy applicability, ProgrammedEvidenceCheckService checks,
                                 VerificationResultPort results, RedmineTicketReader tickets) {
        this.runs = runs;
        this.loader = loader;
        this.applicability = applicability;
        this.checks = checks;
        this.results = results;
        this.tickets = tickets;
    }

    @Async("verificationTaskExecutor")
    public void process(UUID runId) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("runId", runId.toString())) {
        if (!runs.markRunning(runId)) {
            log.warn("verification_transition_rejected phase=RUNNING runId={}", runId);
            return;
        }
        log.info("verification_started runId={}", runId);
        try {
            VerificationRunSnapshot run = runs.findById(runId)
                    .orElseThrow(() -> new IllegalStateException("Run disappeared after transition"));
            VerificationRunWorkLoader.Work work = loader.load(run);
            for (VerificationRunWorkLoader.WorkItem item : work.items()) {
                ExpectationContext expectation = item.expectation();
                if (expectation.registry().evidenceType() == EvidenceType.TICKET) {
                    var selection = run.ticketSelections().groups().stream()
                            .filter(group -> group.groupId().equals(expectation.ticketSelectionGroupId()))
                            .findFirst().orElseThrow(() -> new IllegalStateException("Ticket selection is missing"));
                    for (String ticketId : selection.ticketIds()) {
                        evaluate(run, work, item, ticketId);
                    }
                } else {
                    evaluate(run, work, item, null);
                }
            }
            VerificationResultProgress progress = results.progress(runId);
            if (progress.resultCount() != run.total() || !runs.markCompleted(runId)) {
                throw new IllegalStateException("Persisted result count does not match expected total");
            }
            log.info("verification_completed runId={} processed={} total={}", runId,
                    progress.resultCount(), run.total());
        } catch (Exception ex) {
            log.error("verification_failed runId={} errorType={}", runId, ex.getClass().getSimpleName());
            runs.markFailed(runId, "La verificación no pudo completarse por un error interno.");
        }
        }
    }

    private void evaluate(VerificationRunSnapshot run, VerificationRunWorkLoader.Work work,
                          VerificationRunWorkLoader.WorkItem item, String ticketId) {
        ExpectationContext expectation = item.expectation();
        List<AiApplicabilityRequest.Fact> facts = new ArrayList<>();
        if (run.note() != null && !run.note().isBlank()) {
            facts.add(new AiApplicabilityRequest.Fact("run:note", "Nota de la verificación", run.note()));
        }
        if (expectation.applicability() == ApplicabilityMode.CASE_DEPENDENT
                && expectation.ticketSelectionGroupId() != null && run.redmineProjectId() != null) {
            var selection = run.ticketSelections().groups().stream()
                    .filter(group -> group.groupId().equals(expectation.ticketSelectionGroupId()))
                    .findFirst();
            if (selection.isPresent()) {
                for (String selected : ticketId == null ? selection.get().ticketIds() : List.of(ticketId)) {
                    try {
                        TicketSnapshot ticket = tickets.getTicket(run.redmineProjectId(), selected);
                        if (ticket != null && selected.equals(ticket.id())
                                && run.redmineProjectId().equals(ticket.projectId())) {
                            addFact(facts, selected, "subject", "Asunto del ticket", ticket.subject());
                            addFact(facts, selected, "description", "Descripción del ticket", ticket.description());
                            addFact(facts, selected, "status", "Estado del ticket", ticket.statusName());
                            if (ticket.closed() != null) {
                                addFact(facts, selected, "closed", "Cierre del ticket", ticket.closed().toString());
                            }
                            int fieldIndex = 0;
                            for (TicketSnapshot.CustomField field : ticket.customFields().stream().limit(20).toList()) {
                                addFact(facts, selected, "custom:" + fieldIndex++,
                                        "Campo personalizado: " + field.name(),
                                        field.value() == null ? null : String.valueOf(field.value()));
                            }
                            int journalIndex = 0;
                            for (TicketSnapshot.JournalEntry journal : ticket.journals().stream().limit(20).toList()) {
                                int currentJournal = journalIndex++;
                                addFact(facts, selected, "journal:" + currentJournal,
                                        "Nota del historial del ticket", journal.notes());
                                int changeIndex = 0;
                                for (TicketSnapshot.JournalChange change : journal.changes().stream().limit(10).toList()) {
                                    addFact(facts, selected, "change:" + currentJournal + ":" + changeIndex++,
                                            "Cambio del campo " + change.field(),
                                            (change.oldValue() == null ? "" : change.oldValue())
                                                    + " → " + (change.newValue() == null ? "" : change.newValue()));
                                }
                            }
                        }
                    } catch (ApiException ex) {
                        // Inaccessible external facts leave applicability pending; the programmed check
                        // will report the source problem as REVIEW_REQUIRED instead of failing the run.
                    }
                }
            }
        }
        ApplicabilityAssessment decision = applicability.assess(new EvaluationContext(
                expectation.expectationId(), expectation.displayName(), expectation.applicability(),
                run.serviceTypeSnapshot().name(), work.policies(), item.instructions(), facts
        ));
        ProgrammedEvidenceCheckService.Assessment assessment = checks.assess(
                expectation, work.service(), decision.decision(), ticketId,
                work.policies(), item.instructions()
        );
        List<VerificationResultWriteCommand.EvidenceReference> evidence = assessment.evidenceReferences().stream()
                .filter(reference -> reference.kind() != null && reference.source() != null
                        && reference.externalId() != null && reference.displayName() != null
                        && reference.observedAt() != null)
                .map(reference -> new VerificationResultWriteCommand.EvidenceReference(
                        reference.kind(), reference.source(), reference.externalId(), reference.displayName(),
                        reference.webUrl(), reference.observedAt(), reference.ticketId()
                )).toList();
        results.saveEvaluatedResult(new VerificationResultWriteCommand(
                run.id(), expectation.expectationId(), expectation.macroActivityId(), expectation.activityId(),
                expectation.displayName(), ticketId, decision.decision(), decision.origin(),
                decision.rationale(), decision.limitations(), assessment.proposedStatus(),
                assessment.rationale(), assessment.limitations(), evidence
        ));
        log.info("verification_result_saved runId={} expectationId={} ticketId={} proposedStatus={}",
                run.id(), expectation.expectationId(), ticketId, assessment.proposedStatus());
    }

    private static void addFact(List<AiApplicabilityRequest.Fact> facts, String ticketId,
                                String field, String label, String value) {
        if (value != null && !value.isBlank()) {
            facts.add(new AiApplicabilityRequest.Fact("ticket:" + ticketId + ":" + field,
                    label + " #" + ticketId, value.length() > 2000 ? value.substring(0, 2000) : value));
        }
    }
}
