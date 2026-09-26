package com.temaconsulting.agente_iso.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReaderRequest(
        @NotBlank(message = "email es obligatorio")
        @Email(message = "email debe tener formato válido")
        String email,

        @NotBlank(message = "displayName es obligatorio")
        String displayName,

        @NotBlank(message = "initialPassword es obligatoria")
        @Size(min = 8, message = "initialPassword debe tener al menos 8 caracteres")
        String initialPassword
) {
}
