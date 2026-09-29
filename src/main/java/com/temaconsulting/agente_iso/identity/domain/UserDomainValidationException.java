package com.temaconsulting.agente_iso.identity.domain;

/**
 * Indicates that a user invariant is being violated while creating or
 * changing a domain User.
 */
public class UserDomainValidationException extends RuntimeException {

    public UserDomainValidationException(String message) {
        super(message);
    }
}
