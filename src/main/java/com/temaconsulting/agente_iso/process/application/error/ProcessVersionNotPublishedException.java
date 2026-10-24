package com.temaconsulting.agente_iso.process.application.error;

import java.util.UUID;

public final class ProcessVersionNotPublishedException extends RuntimeException {
    private final UUID versionId;

    public ProcessVersionNotPublishedException(UUID versionId) {
        super("Process version is not published: " + versionId);
        this.versionId = versionId;
    }

    public UUID versionId() {
        return versionId;
    }
}
