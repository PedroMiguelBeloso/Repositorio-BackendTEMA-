package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.error.VerificationResultQueryFailure;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultRepository;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultValidationException;

import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VerificationResultQueryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final VerificationResultRepository results;

    public VerificationResultQueryService(VerificationResultRepository results) {
        this.results = results;
    }

    public Page<VerificationResult> findCompletedRunResults(UUID runId, int page, int size) {
        if (page < 0) {
            throw new VerificationResultValidationException("page", "page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new VerificationResultValidationException("size", "size must be between 1 and 100");
        }
        String status = results.findRunStatus(runId)
                .orElseThrow(() -> VerificationResultQueryFailure.notFound(runId));
        if (!"COMPLETED".equals(status)) {
            throw VerificationResultQueryFailure.notCompleted(runId, status);
        }
        return results.findPage(runId, page, size);
    }
}
