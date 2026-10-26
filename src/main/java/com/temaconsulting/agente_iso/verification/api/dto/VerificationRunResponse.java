package com.temaconsulting.agente_iso.verification.api.dto;

import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VerificationRunResponse(
        UUID id,
        UUID serviceId,
        ServiceType serviceTypeSnapshot,
        int serviceRevisionSnapshot,
        UUID processVersionId,
        ServiceSourceSnapshot sourceSnapshot,
        VerificationScope scope,
        UUID macroActivityId,
        String note,
        String status,
        RunProgress progress,
        UUID startedBy,
        Instant startedAt,
        Instant finishedAt,
        String failureMessage,
        List<TicketSelectionResponse> ticketSelections,
        String serviceNameSnapshot,
        String processNameSnapshot,
        String processOfficialVersionSnapshot,
        String macroActivityNameSnapshot
) {
    public VerificationRunResponse {
        ticketSelections = List.copyOf(ticketSelections);
    }

    public record ServiceSourceSnapshot(
            String driveFolderId,
            String driveFolderName,
            String redmineProjectId,
            String redmineProjectName
    ) {
    }

    public record RunProgress(int total, int processed, int reviewRequired) {
    }

    public record TicketSelectionResponse(String groupId, List<String> ticketIds) {
        public TicketSelectionResponse {
            ticketIds = List.copyOf(ticketIds);
        }
    }
}
