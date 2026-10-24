package com.temaconsulting.agente_iso.integration.infrastructure.drive;

import com.temaconsulting.agente_iso.integration.domain.SourceHealth;

final class DriveReadException extends RuntimeException {
    private final SourceHealth health;

    DriveReadException(SourceHealth health, Throwable cause) {
        super(cause);
        this.health = health;
    }

    SourceHealth health() {
        return health;
    }
}
