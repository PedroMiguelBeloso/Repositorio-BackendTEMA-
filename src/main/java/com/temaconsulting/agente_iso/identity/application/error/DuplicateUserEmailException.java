package com.temaconsulting.agente_iso.identity.application.error;

/** Raised when a user email is already registered. */
public class DuplicateUserEmailException extends RuntimeException {

    public DuplicateUserEmailException() {
        super("Ya existe un usuario con ese email.");
    }
}
