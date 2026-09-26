package com.temaconsulting.agente_iso.process.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProcessVersion(
        UUID id,
        UUID processId,
        String officialVersion,
        LocalDate versionDate,
        ProcessStatus status,
        int revision,
        String objective,
        String owner,
        Instant publishedAt,
        Instant createdAt
) {
    public boolean isEditable() {
        return status == ProcessStatus.DRAFT;
    }
}
