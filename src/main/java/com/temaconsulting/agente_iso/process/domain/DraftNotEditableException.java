package com.temaconsulting.agente_iso.process.domain;

import java.util.UUID;

public class DraftNotEditableException extends RuntimeException {
    public DraftNotEditableException(UUID versionId) {
        super("Process version is not editable: " + versionId);
    }
}
