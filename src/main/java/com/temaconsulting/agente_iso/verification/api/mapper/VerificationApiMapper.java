package com.temaconsulting.agente_iso.verification.api.mapper;

import com.temaconsulting.agente_iso.verification.api.dto.PreviewExpectationResponse;
import com.temaconsulting.agente_iso.verification.api.dto.TicketSelectionGroupResponse;
import com.temaconsulting.agente_iso.verification.api.dto.VerificationPreviewResponse;
import com.temaconsulting.agente_iso.verification.domain.PreviewExpectation;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;

public final class VerificationApiMapper {
    private VerificationApiMapper() {
    }

    public static VerificationPreviewResponse toResponse(VerificationPreview preview) {
        return new VerificationPreviewResponse(
                preview.serviceId(),
                preview.serviceRevision(),
                preview.processVersionId(),
                preview.scope(),
                preview.macroActivityId(),
                preview.expectations().stream().map(VerificationApiMapper::toExpectation).toList(),
                preview.ticketSelectionGroups().stream().map(VerificationApiMapper::toTicketGroup).toList()
        );
    }

    private static PreviewExpectationResponse toExpectation(PreviewExpectation expectation) {
        return new PreviewExpectationResponse(
                expectation.id(),
                expectation.macroActivityId(),
                expectation.activityId(),
                expectation.location(),
                expectation.displayName(),
                expectation.evidenceType(),
                expectation.applicability(),
                expectation.cardinality(),
                expectation.ticketSelectionGroupId()
        );
    }

    private static TicketSelectionGroupResponse toTicketGroup(TicketSelectionGroup group) {
        return new TicketSelectionGroupResponse(
                group.id(),
                group.macroActivityId(),
                group.label(),
                group.cardinality(),
                group.inputExpectationIds(),
                group.relatedExpectationIds(),
                group.required()
        );
    }
}
