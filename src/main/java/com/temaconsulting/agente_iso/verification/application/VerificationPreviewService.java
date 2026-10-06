package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case for the read-only M2 verification preview.
 * It deliberately depends only on public read/query boundaries of M1.
 */
@Service
public class VerificationPreviewService {
    private final VerificationScopeResolver scopeResolver;

    public VerificationPreviewService(VerificationScopeResolver scopeResolver) {
        this.scopeResolver = scopeResolver;
    }

    @Transactional(readOnly = true)
    public VerificationPreview preview(VerificationTarget target) {
        return scopeResolver.resolve(target);
    }
}
