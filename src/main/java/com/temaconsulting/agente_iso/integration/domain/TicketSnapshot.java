package com.temaconsulting.agente_iso.integration.domain;

import java.time.Instant;
import java.util.List;

public record TicketSnapshot(
        String id,
        String projectId,
        String projectName,
        String subject,
        String status,
        boolean closed,
        String description,
        String assignedTo,
        Instant updatedAt,
        Instant createdAt,
        List<CustomField> customFields,
        List<JournalEntry> journals
) {
    public TicketSnapshot {
        customFields = customFields == null ? List.of() : List.copyOf(customFields);
        journals = journals == null ? List.of() : List.copyOf(journals);
    }

    public record CustomField(String id, String name, Object value) {}

    public record JournalEntry(String id, String user, String notes, Instant createdAt) {}
}
