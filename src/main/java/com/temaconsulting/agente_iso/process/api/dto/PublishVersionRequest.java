package com.temaconsulting.agente_iso.process.api.dto;

import jakarta.validation.constraints.Min;

public record PublishVersionRequest(
        @Min(0) int revision
) {
}
