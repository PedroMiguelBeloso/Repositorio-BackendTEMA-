package com.temaconsulting.agente_iso.verification.api.mapper;

import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import com.temaconsulting.agente_iso.verification.api.dto.EvidenceReferenceResponse;
import com.temaconsulting.agente_iso.verification.api.dto.HumanDecisionEventResponse;
import com.temaconsulting.agente_iso.verification.api.dto.VerificationResultResponse;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import org.springframework.stereotype.Component;

@Component
public class VerificationResultApiMapper {
    public VerificationResultResponse toResponse(VerificationResult result) {
        return new VerificationResultResponse(
                result.id(),
                result.runId(),
                result.expectationId(),
                result.macroActivityId(),
                result.activityId(),
                result.displayName(),
                EvidenceType.valueOf(result.evidenceType().name()),
                result.location(),
                result.subjectTicketId(),
                com.temaconsulting.agente_iso.verification.api.dto.ApplicabilityAssessmentResponse
                        .from(result.applicability()),
                result.proposedStatus(),
                result.effectiveStatus(),
                result.rationale(),
                result.limitations(),
                result.evidence().stream()
                        .map(reference -> new EvidenceReferenceResponse(
                                EvidenceType.valueOf(reference.kind().name()),
                                reference.source(),
                                reference.externalId(),
                                reference.displayName(),
                                reference.webUrl(),
                                reference.observedAt(),
                                reference.ticketId()
                        ))
                        .toList(),
                result.humanDecisions().stream()
                        .map(decision -> new HumanDecisionEventResponse(
                                decision.id(),
                                decision.decisionType(),
                                decision.applicabilityDecision(),
                                decision.finalStatus(),
                                decision.rationale(),
                                decision.decidedBy(),
                                decision.decidedAt()
                        ))
                        .toList(),
                result.revision()
        );
    }
}
