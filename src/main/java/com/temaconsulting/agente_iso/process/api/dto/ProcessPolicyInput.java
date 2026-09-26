package com.temaconsulting.agente_iso.process.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ProcessPolicyInput(
        UUID id,
        @Min(0) int order,
        @NotBlank String text
) {
}
