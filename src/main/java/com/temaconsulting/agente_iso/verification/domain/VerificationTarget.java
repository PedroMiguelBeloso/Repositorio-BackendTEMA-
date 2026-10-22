package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.verification.domain.error.VerificationDomainValidationException;

import java.util.UUID;

/**
 * Domain value object representing the target of a verification preview.
 * Its scope/identifier relationship is a business invariant, not an HTTP concern.
 */
public record VerificationTarget(
        UUID serviceId,
        UUID processVersionId,
        VerificationScope scope,
        UUID macroActivityId,
        String note
) {
    public VerificationTarget {
        if (serviceId == null) {
            throw new VerificationDomainValidationException("serviceId", "Service ID is required");
        }
        if (processVersionId == null) {
            throw new VerificationDomainValidationException("processVersionId", "Process version ID is required");
        }
        if (scope == null) {
            throw new VerificationDomainValidationException("scope", "Scope is required");
        }

        if (scope == VerificationScope.MACRO_ACTIVITY && macroActivityId == null) {
            throw new VerificationDomainValidationException(
                    "macroActivityId",
                    "Macro activity ID is required when scope is MACRO_ACTIVITY"
            );
        }
        if (scope == VerificationScope.PROCESS && macroActivityId != null) {
            throw new VerificationDomainValidationException(
                    "macroActivityId",
                    "Macro activity ID must be omitted when scope is PROCESS"
            );
        }

        note = normalizeNote(note);
    }

    private static String normalizeNote(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new VerificationDomainValidationException(
                    "note",
                    "Note must have at most 1000 characters"
            );
        }
        return normalized;
    }
}
