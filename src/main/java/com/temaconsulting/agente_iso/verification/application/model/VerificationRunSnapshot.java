package com.temaconsulting.agente_iso.verification.application.model;

import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;

import java.time.Instant;
import java.util.UUID;

public record VerificationRunSnapshot(
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
        Instant finishedAt,
        String failureMessage,
        int total,
        ValidatedTicketSelections ticketSelections,
        String serviceNameSnapshot,
        String processNameSnapshot,
        String processOfficialVersionSnapshot,
        String macroActivityNameSnapshot
) {
    public VerificationRunSnapshot(UUID id, UUID serviceId, ServiceType serviceTypeSnapshot,
                                   int serviceRevisionSnapshot, UUID processVersionId,
                                   String driveFolderId, String driveFolderName,
                                   String redmineProjectId, String redmineProjectName,
                                   VerificationScope scope, UUID macroActivityId, String note,
                                   String status, UUID startedBy, Instant startedAt, Instant finishedAt,
                                   String failureMessage, int total, ValidatedTicketSelections ticketSelections) {
        this(id, serviceId, serviceTypeSnapshot, serviceRevisionSnapshot, processVersionId,
                driveFolderId, driveFolderName, redmineProjectId, redmineProjectName,
                scope, macroActivityId, note, status, startedBy, startedAt, finishedAt,
                failureMessage, total, ticketSelections, null, null, null, null);
    }
}
