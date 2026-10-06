package com.temaconsulting.agente_iso.integration.domain;

import java.time.Instant;
import java.util.List;

public record TicketSnapshot(
        String id,
        String projectId,
        String projectName,
        String subject,
        String status,
        String statusId,
        String statusName,
        Boolean closed,
        String description,
        String assignedTo,
        Instant updatedAt,
        Instant createdAt,
        List<CustomField> customFields,
        List<JournalEntry> journals
) {
    public TicketSnapshot {
        if (statusName == null) {
            statusName = status;
        }
        if (status == null) {
            status = statusName;
        }
        statusId = statusId == null || statusId.isBlank() ? null : statusId.trim();
        customFields = customFields == null ? List.of() : List.copyOf(customFields);
        journals = journals == null ? List.of() : List.copyOf(journals);
    }

    public record CustomField(String id, String name, Object value) {}

    public record JournalEntry(String id, String user, String notes, Instant createdAt, List<JournalChange> changes) {
        public JournalEntry {
            changes = changes == null ? List.of() : List.copyOf(changes);
        }
    }

    public record JournalChange(String field, Object oldValue, Object newValue) {}
}
