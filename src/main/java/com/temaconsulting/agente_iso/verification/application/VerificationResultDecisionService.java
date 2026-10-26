package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.error.VerificationResultDecisionFailure;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultDecisionTarget;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultRepository;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultValidationException;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class VerificationResultDecisionService {
    private static final Logger log = LoggerFactory.getLogger(VerificationResultDecisionService.class);
    private final VerificationResultRepository results;
    private final Clock clock;

    public VerificationResultDecisionService(VerificationResultRepository results, Clock clock) {
        this.results = results;
        this.clock = clock;
    }

    @Transactional
    public VerificationResult decide(
            UUID runId,
            UUID resultId,
            int revision,
            ResultStatus finalStatus,
            String rationale,
            UUID decidedBy
    ) {
        if (rationale == null || rationale.isBlank()) {
            throw new VerificationResultValidationException("rationale", "rationale must not be blank");
        }
        VerificationResultDecisionTarget target = results.lockDecisionTarget(runId, resultId)
                .orElseThrow(VerificationResultDecisionFailure::notFound);
        if (!"COMPLETED".equals(target.runStatus()) || revision != target.revision()) {
            throw VerificationResultDecisionFailure.conflict();
        }
        if (!isStatusAllowed(target, finalStatus)) {
            throw VerificationResultDecisionFailure.incompatibleStatus();
        }
        VerificationResult result = results.saveStatusDecision(
                runId,
                resultId,
                revision,
                finalStatus,
                rationale.trim(),
                decidedBy,
                clock.instant()
        );
        log.info("verification_result_decided runId={} resultId={} status={} actorId={}",
                runId, resultId, finalStatus, decidedBy);
        return result;
    }

    private boolean isStatusAllowed(VerificationResultDecisionTarget target, ResultStatus status) {
        if (target.effectiveApplicability() == ApplicabilityDecision.APPLIES) {
            return status == ResultStatus.COMPLIES
                    || status == ResultStatus.DOES_NOT_COMPLY
                    || status == ResultStatus.REVIEW_REQUIRED;
        }
        return target.effectiveApplicability() == ApplicabilityDecision.NOT_APPLICABLE
                && target.applicabilityMode() == ApplicabilityMode.CASE_DEPENDENT
                && status == ResultStatus.NOT_APPLICABLE;
    }
}
