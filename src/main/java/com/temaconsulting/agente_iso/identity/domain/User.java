package com.temaconsulting.agente_iso.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String displayName,
        String passwordHash,
        Role role,
        boolean active,
        Instant createdAt
) {

    public boolean canLogin() {
        return active;
    }
}
