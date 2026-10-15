package com.temaconsulting.agente_iso.verification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ResolveApplicabilityRequest(
        @NotNull @Positive Integer revision,
        @NotNull Decision decision,
        @NotBlank String rationale
) {
    public enum Decision { APPLIES, NOT_APPLICABLE }
}
