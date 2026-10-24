package com.temaconsulting.agente_iso.process.application.error;

import java.util.UUID;

public final class ProcessVersionNotFoundException extends RuntimeException {
    private final UUID versionId;

    public ProcessVersionNotFoundException(UUID versionId) {
        super("Process version not found: " + versionId);
        this.versionId = versionId;
    }

    public UUID versionId() {
        return versionId;
    }
}
