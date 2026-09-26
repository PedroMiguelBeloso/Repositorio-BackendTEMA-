package com.temaconsulting.agente_iso.identity.application;

import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.springframework.http.HttpStatus;

import java.util.List;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(
                ProblemCode.UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED,
                "Las credenciales no son válidas.",
                List.of()
        );
    }
}
