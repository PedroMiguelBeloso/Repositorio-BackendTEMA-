package com.temaconsulting.agente_iso.identity.api.dto;

import com.temaconsulting.agente_iso.identity.domain.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        Role role,
        boolean active,
        Instant createdAt
) {
}
