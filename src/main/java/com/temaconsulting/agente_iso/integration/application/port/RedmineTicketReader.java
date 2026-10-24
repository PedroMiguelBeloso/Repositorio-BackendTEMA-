package com.temaconsulting.agente_iso.integration.application.port;

import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;

public interface RedmineTicketReader {
    TicketSnapshot getTicket(String projectId, String ticketId);
}
