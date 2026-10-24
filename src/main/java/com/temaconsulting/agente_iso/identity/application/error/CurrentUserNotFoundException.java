package com.temaconsulting.agente_iso.identity.application.error;

import java.util.UUID;

/** Raised when the authenticated subject no longer maps to a usable user. */
public class CurrentUserNotFoundException extends RuntimeException {

    private final UUID userId;

    public CurrentUserNotFoundException(UUID userId) {
        super("La sesión ya no corresponde a un usuario válido.");
        this.userId = userId;
    }

    public UUID userId() {
        return userId;
    }
}
