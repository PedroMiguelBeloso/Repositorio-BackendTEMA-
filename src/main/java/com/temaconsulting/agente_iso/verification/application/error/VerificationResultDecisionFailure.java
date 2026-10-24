package com.temaconsulting.agente_iso.verification.application.error;

public class VerificationResultDecisionFailure extends RuntimeException {
    public enum Kind {
        NOT_FOUND,
        CONFLICT,
        INCOMPATIBLE_STATUS,
        INCOMPATIBLE_APPLICABILITY
    }

    private final Kind kind;

    private VerificationResultDecisionFailure(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static VerificationResultDecisionFailure notFound() {
        return new VerificationResultDecisionFailure(
                Kind.NOT_FOUND,
                "Verification run or result not found"
        );
    }

    public static VerificationResultDecisionFailure conflict() {
        return new VerificationResultDecisionFailure(
                Kind.CONFLICT,
                "The run is not completed or the result revision is stale"
        );
    }

    public static VerificationResultDecisionFailure incompatibleStatus() {
        return new VerificationResultDecisionFailure(
                Kind.INCOMPATIBLE_STATUS,
                "Final status is incompatible with the effective applicability"
        );
    }

    public static VerificationResultDecisionFailure incompatibleApplicability() {
        return new VerificationResultDecisionFailure(Kind.INCOMPATIBLE_APPLICABILITY,
                "Only pending CASE_DEPENDENT applicability can be resolved");
    }

    public Kind kind() {
        return kind;
    }
}
