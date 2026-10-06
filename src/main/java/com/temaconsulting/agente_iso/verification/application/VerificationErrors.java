package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.shared.application.ProblemCode;

final class VerificationErrors {
    private VerificationErrors() {
    }

    static VerificationPreviewFailure invalid(String field, String message) {
        return VerificationPreviewFailure.invalid(field, message);
    }

    static VerificationPreviewFailure rule(String field, String message) {
        return VerificationPreviewFailure.businessRule(ProblemCode.VALIDATION_ERROR, field, message);
    }

    static VerificationPreviewFailure unsupportedScope(String field, String message) {
        return VerificationPreviewFailure.businessRule(ProblemCode.UNSUPPORTED_SCOPE, field, message);
    }

    static VerificationPreviewFailure ticketSelectionRule(String field, String message) {
        return VerificationPreviewFailure.businessRule(ProblemCode.TICKET_SELECTION_INVALID, field, message);
    }

    static VerificationPreviewFailure missing(String message) {
        return VerificationPreviewFailure.missing(message);
    }
}
