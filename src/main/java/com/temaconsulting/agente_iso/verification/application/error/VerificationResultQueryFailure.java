package com.temaconsulting.agente_iso.verification.application.error;

import java.util.UUID;

public class VerificationResultQueryFailure extends RuntimeException {
    public enum Kind {
        NOT_FOUND,
        NOT_COMPLETED
    }

    private final Kind kind;

    private VerificationResultQueryFailure(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static VerificationResultQueryFailure notFound(UUID runId) {
        return new VerificationResultQueryFailure(Kind.NOT_FOUND, "Verification run not found: " + runId);
    }

    public static VerificationResultQueryFailure notCompleted(UUID runId, String status) {
        return new VerificationResultQueryFailure(
                Kind.NOT_COMPLETED,
                "Results are available only for completed runs; run " + runId + " is " + status
        );
    }

    public Kind kind() {
        return kind;
    }
}
