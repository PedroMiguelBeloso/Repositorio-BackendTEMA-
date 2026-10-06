package com.temaconsulting.agente_iso.verification.api.dto;

import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerificationTarget(
        @NotNull UUID serviceId,
        @NotNull UUID processVersionId,
        @NotNull VerificationScope scope,
        UUID macroActivityId,
        @Size(max = 1000) String note
) {
    public com.temaconsulting.agente_iso.verification.domain.VerificationTarget toDomain() {
        return new com.temaconsulting.agente_iso.verification.domain.VerificationTarget(
                serviceId, processVersionId, scope, macroActivityId, note);
    }
}
