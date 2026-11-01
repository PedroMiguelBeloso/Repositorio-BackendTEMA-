package com.temaconsulting.agente_iso.verification.application.port;

import com.temaconsulting.agente_iso.verification.application.model.AiRetryAttempt;
import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import java.util.List;
import java.util.UUID;

public interface AiRetryRepository {
    VerificationResult save(AiRetryAttempt attempt, ApplicabilityAssessment applicability,
                            List<String> limitations,
                            List<VerificationResultWriteCommand.EvidenceReference> evidence);
    List<AiRetryAttempt> list(UUID runId, UUID resultId);
}
