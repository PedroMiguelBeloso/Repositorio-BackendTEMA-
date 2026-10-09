package com.temaconsulting.agente_iso.verification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TicketSelectionRequest(
        @NotBlank String groupId,
        @NotEmpty List<String> ticketIds
) {
}
