package com.temaconsulting.agente_iso.process.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateVersionRequest(
        String officialVersion,
        LocalDate versionDate,
        UUID copyFromVersionId
) {
}
