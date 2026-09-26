package com.temaconsulting.agente_iso.identity.api.dto;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user
) {
}
