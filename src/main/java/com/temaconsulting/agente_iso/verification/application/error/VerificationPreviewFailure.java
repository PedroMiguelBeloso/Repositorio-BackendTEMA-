package com.temaconsulting.agente_iso.verification.application.error;

import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;

/**
 * Application-layer failure expressed in domain/application terms. The API
 * adapter is responsible for mapping it to HTTP.
 */
public final class VerificationPreviewFailure extends RuntimeException {
    public enum Kind { INVALID_INPUT, NOT_FOUND, BUSINESS_RULE }

    private final Kind kind;
    private final ProblemCode code;
    private final String field;

    private VerificationPreviewFailure(Kind kind, ProblemCode code, String field, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.field = field;
    }

    public static VerificationPreviewFailure invalid(String field, String message) {
        return new VerificationPreviewFailure(Kind.INVALID_INPUT, ProblemCode.VALIDATION_ERROR, field, message);
    }

    public static VerificationPreviewFailure missing(String message) {
        return new VerificationPreviewFailure(Kind.NOT_FOUND, ProblemCode.NOT_FOUND, null, message);
    }

    public static VerificationPreviewFailure missing(ProblemCode code, String message) {
        return new VerificationPreviewFailure(Kind.NOT_FOUND, code, null, message);
    }

    public static VerificationPreviewFailure businessRule(ProblemCode code, String field, String message) {
        return new VerificationPreviewFailure(Kind.BUSINESS_RULE, code, field, message);
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
