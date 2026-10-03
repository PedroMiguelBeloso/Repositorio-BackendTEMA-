package com.temaconsulting.agente_iso.service.domain;

import java.util.Objects;
import java.util.UUID;

public record ServiceProcessAssignment(UUID processId, UUID processVersionId) {
    public ServiceProcessAssignment {
        Objects.requireNonNull(processId, "processId is required");
        Objects.requireNonNull(processVersionId, "processVersionId is required");
    }
}
