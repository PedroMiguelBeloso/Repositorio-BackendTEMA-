package com.temaconsulting.agente_iso.verification.application.model;

import java.util.List;

public record TicketSelectionInput(String groupId, List<String> ticketIds) {
}
