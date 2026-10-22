package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.error.VerificationPreviewFailure;

import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;

import java.util.UUID;

final class VerificationErrors {
    private VerificationErrors() {
    }

    static VerificationPreviewFailure ticketSelectionRule(String field, String message) {
        return VerificationPreviewFailure.businessRule(ProblemCode.TICKET_SELECTION_INVALID, field, message);
    }

    static VerificationPreviewFailure serviceNotFound(UUID serviceId) {
        return VerificationPreviewFailure.missing(
                ProblemCode.SERVICE_NOT_FOUND,
                "Service not found: " + serviceId
        );
    }

    static VerificationPreviewFailure processVersionNotFound(UUID versionId) {
        return VerificationPreviewFailure.missing(
                ProblemCode.PROCESS_VERSION_NOT_FOUND,
                "Process version not found: " + versionId
        );
    }

    static VerificationPreviewFailure processVersionNotPublished(UUID versionId) {
        return VerificationPreviewFailure.businessRule(
                ProblemCode.PROCESS_VERSION_NOT_PUBLISHED,
                "processVersionId",
                "Process version is not published: " + versionId
        );
    }

    static VerificationPreviewFailure processVersionNotAssigned(UUID versionId, UUID serviceId) {
        return VerificationPreviewFailure.businessRule(
                ProblemCode.PROCESS_VERSION_NOT_ASSIGNED,
                "processVersionId",
                "Process version " + versionId + " is not assigned to service " + serviceId
        );
    }

    static VerificationPreviewFailure processNotEnabledForM2(UUID versionId) {
        return VerificationPreviewFailure.businessRule(
                ProblemCode.PROCESS_NOT_M2_ENABLED,
                "processVersionId",
                "Process version " + versionId + " is not enabled for M2 verification"
        );
    }

    static VerificationPreviewFailure macroActivityNotInVersion(UUID macroActivityId, UUID versionId) {
        return VerificationPreviewFailure.businessRule(
                ProblemCode.MACRO_ACTIVITY_NOT_IN_VERSION,
                "macroActivityId",
                "Macro activity " + macroActivityId + " does not belong to process version " + versionId
        );
    }

    static VerificationPreviewFailure registryDefinitionNotFound(UUID expectationId, UUID registryId) {
        return VerificationPreviewFailure.businessRule(
                ProblemCode.CATALOG_DEFINITION_NOT_FOUND,
                "expectationId",
                "Expectation " + expectationId + " references missing catalog definition " + registryId
        );
    }

    static VerificationPreviewFailure inconsistent(String field, String message) {
        return VerificationPreviewFailure.businessRule(
                ProblemCode.PUBLISHED_CONFIGURATION_INCONSISTENT,
                field,
                message
        );
    }
}
