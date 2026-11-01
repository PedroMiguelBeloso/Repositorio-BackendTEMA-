package com.temaconsulting.agente_iso.verification.api.mapper;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.verification.api.dto.EvidenceSearchStatus;
import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationResultDiagnosticsTest {
    @Test
    void retryReasonExplainsAutomaticProposalDespiteHumanStatusDecision() {
        Instant now = Instant.parse("2026-10-26T12:00:00Z");
        VerificationResult result = new VerificationResult(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, "Ticket con requerimiento",
                EvidenceType.TICKET, EvidenceLocation.INPUT, "2",
                new ApplicabilityAssessment(ApplicabilityDecision.APPLIES, DecisionOrigin.DETERMINISTIC,
                        "Siempre aplica", List.of()), ResultStatus.REVIEW_REQUIRED,
                ResultStatus.DOES_NOT_COMPLY,
                "Nueva propuesta de Gemini: No fue posible interpretar de forma confiable la evidencia del ticket.",
                List.of(), List.of(),
                List.of(new HumanDecisionEvent(UUID.randomUUID(), HumanDecisionType.RESULT_STATUS,
                        null, ResultStatus.DOES_NOT_COMPLY, "Decisión humana", UUID.randomUUID(), now)), 3);

        assertThat(VerificationResultDiagnostics.reviewReason(result))
                .isEqualTo(com.temaconsulting.agente_iso.verification.api.dto.ReviewReasonCode.AI_INCONCLUSIVE);
    }

    @Test
    void resolvedApplicabilityUsesCurrentCheckWithoutLosingOriginalProposal() {
        Instant now = Instant.parse("2026-10-26T12:00:00Z");
        VerificationResult result = new VerificationResult(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, "Servicio en Redmine",
                EvidenceType.TOOL_RECORD, EvidenceLocation.INPUT, null,
                new ApplicabilityAssessment(ApplicabilityDecision.APPLIES, DecisionOrigin.HUMAN,
                        "Aplica al caso", List.of()), ResultStatus.REVIEW_REQUIRED, ResultStatus.COMPLIES,
                "Se comprobó el proyecto Redmine", List.of("No se evaluaron permisos individuales"),
                List.of(new VerificationEvidenceReference(EvidenceType.TOOL_RECORD, IntegrationSource.REDMINE,
                        "1", "Proyecto", null, now, null)),
                List.of(new HumanDecisionEvent(UUID.randomUUID(), HumanDecisionType.APPLICABILITY,
                        HumanApplicabilityDecision.APPLIES, null, "Aplica", UUID.randomUUID(), now)), 2);

        var reason = VerificationResultDiagnostics.reviewReason(result);
        assertThat(reason).isNull();
        assertThat(VerificationResultDiagnostics.searchStatus(result, reason))
                .isEqualTo(EvidenceSearchStatus.FOUND);
        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
    }

    @Test
    void inaccessibleConfiguredDriveFolderIsAnIncompleteSearch() {
        VerificationResult result = new VerificationResult(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, "Mail WON",
                EvidenceType.DOCUMENT, EvidenceLocation.INPUT, null,
                new ApplicabilityAssessment(ApplicabilityDecision.APPLIES, DecisionOrigin.DETERMINISTIC,
                        "Siempre aplica", List.of()), ResultStatus.REVIEW_REQUIRED,
                ResultStatus.REVIEW_REQUIRED,
                "No se pudo completar una comprobación confiable en Google Drive.",
                List.of("No se pudo acceder a la carpeta de Drive configurada para el servicio; puede faltar permiso o la carpeta ya no existir."),
                List.of(), List.of(), 1);

        var reason = VerificationResultDiagnostics.reviewReason(result);
        assertThat(reason).isEqualTo(com.temaconsulting.agente_iso.verification.api.dto.ReviewReasonCode.SOURCE_UNAVAILABLE);
        assertThat(VerificationResultDiagnostics.searchStatus(result, reason))
                .isEqualTo(EvidenceSearchStatus.INCOMPLETE);
    }
}
