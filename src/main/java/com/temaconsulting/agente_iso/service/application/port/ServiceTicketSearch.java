package com.temaconsulting.agente_iso.service.application.port;

import com.temaconsulting.agente_iso.service.application.model.TicketSummary;

import com.temaconsulting.agente_iso.shared.application.model.Page;

/** Redmine adapter must restrict every query to the supplied project ID. */
public interface ServiceTicketSearch {
    Page<TicketSummary> search(String projectId, String query, int page, int size);
}
