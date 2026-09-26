package com.temaconsulting.agente_iso.identity.application;

import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

public class CurrentUserNotFoundException extends ApiException {

    public CurrentUserNotFoundException(UUID id) {
        super(
                ProblemCode.UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED,
                "La sesión ya no corresponde a un usuario válido.",
                List.of()
        );
    }
}
