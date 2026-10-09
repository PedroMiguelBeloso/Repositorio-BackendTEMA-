package com.temaconsulting.agente_iso.verification.application;

import java.util.List;

public record TicketSelectionInput(String groupId, List<String> ticketIds) {
}
