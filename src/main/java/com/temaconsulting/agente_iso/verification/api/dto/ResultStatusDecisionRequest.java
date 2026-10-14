package com.temaconsulting.agente_iso.verification.api.dto;

import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ResultStatusDecisionRequest(
        @NotNull @Positive Integer revision,
        @NotNull ResultStatus finalStatus,
        @NotBlank String rationale
) {
}
