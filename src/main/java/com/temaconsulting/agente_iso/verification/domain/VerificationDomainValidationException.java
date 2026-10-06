package com.temaconsulting.agente_iso.verification.domain;

/**
 * Raised when a verification-domain invariant would create an invalid object.
 * The domain deliberately has no dependency on Spring or HTTP concepts.
 */
public final class VerificationDomainValidationException extends RuntimeException {
    private final String field;

    public VerificationDomainValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
