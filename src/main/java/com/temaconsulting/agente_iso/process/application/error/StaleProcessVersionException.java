package com.temaconsulting.agente_iso.process.application.error;

import java.util.UUID;

public class StaleProcessVersionException extends RuntimeException {
    public StaleProcessVersionException(UUID versionId, int expectedRevision) {
        super("Process version changed before save: " + versionId + " at revision " + expectedRevision);
    }
}
