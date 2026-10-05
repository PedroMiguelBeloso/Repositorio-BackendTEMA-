package com.temaconsulting.agente_iso.integration.application;

import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;

public interface RedmineTicketReader {
    TicketSnapshot getTicket(String projectId, String ticketId);
}
