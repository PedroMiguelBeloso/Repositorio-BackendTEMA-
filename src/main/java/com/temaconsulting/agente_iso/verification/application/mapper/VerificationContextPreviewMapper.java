package com.temaconsulting.agente_iso.verification.application.mapper;

import com.temaconsulting.agente_iso.verification.domain.PreviewExpectation;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;

public final class VerificationContextPreviewMapper {
    private VerificationContextPreviewMapper() {
    }

    public static VerificationPreview toPreview(VerificationContext context) {
        return new VerificationPreview(
                context.service().id(),
                context.service().revision(),
                context.process().versionId(),
                context.scope(),
                context.macroActivityId(),
                context.expectations().stream()
                        .map(expectation -> new PreviewExpectation(
                                expectation.expectationId(),
                                expectation.macroActivityId(),
                                expectation.activityId(),
                                expectation.location(),
                                expectation.displayName(),
                                EvidenceType.valueOf(expectation.registry().evidenceType().name()),
                                expectation.applicability(),
                                expectation.cardinality(),
                                expectation.ticketSelectionGroupId()
                        ))
                        .toList(),
                context.ticketSelectionGroups()
        );
    }
}
