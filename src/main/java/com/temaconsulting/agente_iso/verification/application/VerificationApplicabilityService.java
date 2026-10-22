package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.error.VerificationResultDecisionFailure;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultRecheck;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultRepository;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultValidationException;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.HumanDecisionType;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class VerificationApplicabilityService {
    private final VerificationRunRepository runs;
    private final VerificationRunWorkLoader workLoader;
    private final VerificationResultRepository results;
    private final ProgrammedEvidenceCheckService checks;
    private final Clock clock;

    public VerificationApplicabilityService(VerificationRunRepository runs, VerificationRunWorkLoader workLoader,
            VerificationResultRepository results, ProgrammedEvidenceCheckService checks, Clock clock) {
        this.runs = runs;
        this.workLoader = workLoader;
        this.results = results;
        this.checks = checks;
        this.clock = clock;
    }

    /** No SQL transaction spans the external check. The repository revalidates under a row lock. */
    public VerificationResult resolve(UUID runId, UUID resultId, int revision,
            ApplicabilityDecision decision, String rationale, UUID decidedBy) {
        if (revision < 1) {
            throw new VerificationResultValidationException("revision", "revision must be positive");
        }
        if (decision != ApplicabilityDecision.APPLIES && decision != ApplicabilityDecision.NOT_APPLICABLE) {
            throw new VerificationResultValidationException("decision", "decision must be APPLIES or NOT_APPLICABLE");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new VerificationResultValidationException("rationale", "rationale must not be blank");
        }
        Objects.requireNonNull(decidedBy);

        VerificationRunSnapshot run = runs.findById(runId)
                .orElseThrow(VerificationResultDecisionFailure::notFound);
        VerificationResult current = results.findByRunAndId(runId, resultId)
                .orElseThrow(VerificationResultDecisionFailure::notFound);
        if (!"COMPLETED".equals(run.status()) || current.revision() != revision) {
            throw VerificationResultDecisionFailure.conflict();
        }
        VerificationRunWorkLoader.Work work = workLoader.load(run);
        VerificationRunWorkLoader.WorkItem item = work.items().stream()
                .filter(candidate -> candidate.expectation().expectationId().equals(current.expectationId()))
                .findFirst().orElseThrow(VerificationResultDecisionFailure::notFound);
        var expectation = item.expectation();
        if (expectation.applicability() != ApplicabilityMode.CASE_DEPENDENT) {
            throw VerificationResultDecisionFailure.incompatibleApplicability();
        }
        if (current.applicability().decision() != ApplicabilityDecision.UNDETERMINED
                || current.humanDecisions().stream().anyMatch(e -> e.decisionType() == HumanDecisionType.APPLICABILITY)) {
            throw VerificationResultDecisionFailure.conflict();
        }
        String ticketId = current.subjectTicketId();
        if (expectation.registry().evidenceType() == EvidenceType.TICKET) {
            if (ticketId == null || run.ticketSelections().groups().stream()
                    .noneMatch(group -> group.groupId().equals(expectation.ticketSelectionGroupId())
                            && group.ticketIds().contains(ticketId))) {
                throw VerificationResultDecisionFailure.incompatibleApplicability();
            }
        } else if (ticketId != null) {
            throw VerificationResultDecisionFailure.incompatibleApplicability();
        }

        VerificationResultRecheck recheck;
        if (decision == ApplicabilityDecision.NOT_APPLICABLE) {
            recheck = new VerificationResultRecheck(ResultStatus.NOT_APPLICABLE,
                    "El Administrador resolvió que esta evidencia no aplica.", List.of(), List.of());
        } else {
            var assessment = checks.assess(expectation, work.service(), ApplicabilityDecision.APPLIES, ticketId);
            if (!expectation.expectationId().equals(assessment.expectationId())
                    || !Objects.equals(ticketId, assessment.subjectTicketId())
                    || assessment.proposedStatus() == ResultStatus.NOT_APPLICABLE) {
                throw new IllegalStateException("The recheck did not evaluate the selected result");
            }
            var evidence = assessment.evidenceReferences().stream()
                    .filter(ref -> ref.kind() != null && ref.source() != null && ref.externalId() != null
                            && ref.displayName() != null && ref.observedAt() != null)
                    .map(ref -> new VerificationResultWriteCommand.EvidenceReference(
                            ref.kind(), ref.source(), ref.externalId(), ref.displayName(),
                            ref.webUrl(), ref.observedAt(), ref.ticketId()))
                    .toList();
            recheck = new VerificationResultRecheck(assessment.proposedStatus(), assessment.rationale(),
                    assessment.limitations(), evidence);
        }
        return results.saveApplicabilityDecision(runId, resultId, revision, decision, rationale.trim(),
                decidedBy, clock.instant(), recheck);
    }
}
