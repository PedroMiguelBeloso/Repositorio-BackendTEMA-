package com.temaconsulting.agente_iso.identity.application.error;

/** Raised when the supplied credentials cannot authenticate a user. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Las credenciales no son válidas.");
    }
}
