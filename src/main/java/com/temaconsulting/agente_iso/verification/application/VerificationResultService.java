package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VerificationResultService implements VerificationResultPort {
    private final VerificationResultRepository results;

    public VerificationResultService(VerificationResultRepository results) {
        this.results = results;
    }

    @Override
    public VerificationResult saveEvaluatedResult(VerificationResultWriteCommand command) {
        validateAssessment(command);
        return results.save(command);
    }

    @Override
    public VerificationResultProgress progress(UUID runId) {
        if (results.findRunStatus(runId).isEmpty()) {
            throw VerificationResultQueryFailure.notFound(runId);
        }
        return results.progress(runId);
    }

    @Override
    public Map<UUID, VerificationResultProgress> progress(List<UUID> runIds) {
        return results.progress(runIds);
    }

    private void validateAssessment(VerificationResultWriteCommand command) {
        if (command.applicabilityDecision() == ApplicabilityDecision.NOT_APPLICABLE
                && command.proposedStatus() != ResultStatus.NOT_APPLICABLE) {
            throw new VerificationResultValidationException(
                    "proposedStatus",
                    "A NOT_APPLICABLE assessment must produce a NOT_APPLICABLE result"
            );
        }
        if (command.applicabilityDecision() == ApplicabilityDecision.UNDETERMINED
                && command.proposedStatus() != ResultStatus.REVIEW_REQUIRED) {
            throw new VerificationResultValidationException(
                    "proposedStatus",
                    "An undetermined applicability assessment must produce REVIEW_REQUIRED"
            );
        }
        if (command.proposedStatus() == ResultStatus.NOT_APPLICABLE
                && command.applicabilityDecision() != ApplicabilityDecision.NOT_APPLICABLE) {
            throw new VerificationResultValidationException(
                    "applicabilityDecision",
                    "NOT_APPLICABLE status requires a NOT_APPLICABLE assessment"
            );
        }
    }
}
