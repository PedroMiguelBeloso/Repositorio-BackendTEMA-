package com.temaconsulting.agente_iso.verification.api.dto;

import jakarta.validation.constraints.Min;

public record AiRetryRequest(@Min(1) int revision) {}
