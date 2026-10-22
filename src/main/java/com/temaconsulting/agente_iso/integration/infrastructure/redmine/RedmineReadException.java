package com.temaconsulting.agente_iso.integration.infrastructure.redmine;

import com.temaconsulting.agente_iso.integration.domain.SourceHealth;

final class RedmineReadException extends RuntimeException {
    private final SourceHealth health;

    RedmineReadException(SourceHealth health, Throwable cause) {
        super(cause);
        this.health = health;
    }

    SourceHealth health() {
        return health;
    }
}
