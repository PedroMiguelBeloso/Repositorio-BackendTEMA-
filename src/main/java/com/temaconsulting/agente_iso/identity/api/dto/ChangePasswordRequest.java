package com.temaconsulting.agente_iso.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword es obligatoria")
        String currentPassword,

        @NotBlank(message = "newPassword es obligatoria")
        @Size(min = 8, message = "newPassword debe tener al menos 8 caracteres")
        String newPassword
) {
}
