package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.shared.application.ProblemCode;

public final class VerificationStartFailure extends RuntimeException {
    public enum Kind { INVALID_INPUT, NOT_FOUND, BUSINESS_RULE, CONFLICT }

    private final Kind kind;
    private final ProblemCode code;
    private final String field;

    private VerificationStartFailure(Kind kind, ProblemCode code, String field, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.field = field;
    }

    public static VerificationStartFailure invalid(String field, String message) {
        return new VerificationStartFailure(
                Kind.INVALID_INPUT, ProblemCode.VALIDATION_ERROR, field, message);
    }

    public static VerificationStartFailure missing(String message) {
        return new VerificationStartFailure(Kind.NOT_FOUND, ProblemCode.NOT_FOUND, null, message);
    }

    public static VerificationStartFailure rule(String field, String message) {
        return new VerificationStartFailure(
                Kind.BUSINESS_RULE, ProblemCode.TICKET_SELECTION_INVALID, field, message);
    }

    public static VerificationStartFailure conflict(String field, String message) {
        return new VerificationStartFailure(Kind.CONFLICT, ProblemCode.STALE_REVISION, field, message);
    }

    public Kind kind() {
        return kind;
    }

    public ProblemCode code() {
        return code;
    }

    public String field() {
        return field;
    }
}
