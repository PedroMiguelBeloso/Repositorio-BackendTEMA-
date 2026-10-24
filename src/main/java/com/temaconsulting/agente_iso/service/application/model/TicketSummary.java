package com.temaconsulting.agente_iso.service.application.model;

import java.time.Instant;

public record TicketSummary(
        String id, String subject, String status, String assignedTo, Instant updatedAt, String projectId
) {
}
