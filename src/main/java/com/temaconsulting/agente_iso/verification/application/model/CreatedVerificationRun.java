package com.temaconsulting.agente_iso.verification.application.model;

import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;

import java.time.Instant;
import java.util.UUID;

public record CreatedVerificationRun(
        UUID id,
        UUID serviceId,
        ServiceType serviceTypeSnapshot,
        int serviceRevisionSnapshot,
        UUID processVersionId,
        String driveFolderId,
        String driveFolderName,
        String redmineProjectId,
        String redmineProjectName,
        VerificationScope scope,
        UUID macroActivityId,
        String note,
        String status,
        UUID startedBy,
        Instant startedAt,
        ValidatedTicketSelections ticketSelections,
        int total,
        String serviceNameSnapshot,
        String processNameSnapshot,
        String processOfficialVersionSnapshot,
        String macroActivityNameSnapshot
) {
    public CreatedVerificationRun(UUID id, UUID serviceId, ServiceType serviceTypeSnapshot,
                                  int serviceRevisionSnapshot, UUID processVersionId,
                                  String driveFolderId, String driveFolderName,
                                  String redmineProjectId, String redmineProjectName,
                                  VerificationScope scope, UUID macroActivityId, String note,
                                  String status, UUID startedBy, Instant startedAt,
                                  ValidatedTicketSelections ticketSelections, int total) {
        this(id, serviceId, serviceTypeSnapshot, serviceRevisionSnapshot, processVersionId,
                driveFolderId, driveFolderName, redmineProjectId, redmineProjectName,
                scope, macroActivityId, note, status, startedBy, startedAt,
                ticketSelections, total, null, null, null, null);
    }
}
