package com.temaconsulting.agente_iso.verification.api.mapper;

import com.temaconsulting.agente_iso.verification.api.dto.EvidenceSearchStatus;
import com.temaconsulting.agente_iso.verification.api.dto.ReviewReasonCode;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.HumanDecisionType;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;

import java.util.Locale;

/**
 * Compatibility projection for results stored without diagnostic columns.
 * Unknown rationale is left as OTHER/UNKNOWN rather than guessing that a source
 * was searched. The public codes remain stable as wording evolves.
 */
final class VerificationResultDiagnostics {
    private VerificationResultDiagnostics() { }

    static ReviewReasonCode reviewReason(VerificationResult result) {
        boolean hasHumanStatusDecision = result.humanDecisions().stream()
                .anyMatch(decision -> decision.decisionType() == HumanDecisionType.RESULT_STATUS);
        ResultStatus statusToExplain = hasHumanStatusDecision
                ? result.proposedStatus() : evaluationStatus(result);
        if (statusToExplain != ResultStatus.REVIEW_REQUIRED) return null;
        String text = (result.rationale() + " " + String.join(" ", result.limitations()))
                .toLowerCase(Locale.ROOT);
        if (text.contains("primero falta resolver la aplicabilidad")
                || (result.applicability().decision() == ApplicabilityDecision.UNDETERMINED
                && text.contains("aplicabilidad"))) return ReviewReasonCode.APPLICABILITY_PENDING;
        if (text.contains("no se recibió el id del ticket") || text.contains("no hay un ticket seleccionado"))
            return ReviewReasonCode.MISSING_TICKET_SELECTION;
        if (text.contains("no tiene un lector operativo") || text.contains("no está conectada a un puerto")
                || text.contains("no existe una conexión de lectura"))
            return ReviewReasonCode.UNSUPPORTED_SOURCE;
        if (text.contains("no tiene una carpeta") || text.contains("no hay una carpeta válida")
                || text.contains("no tiene un proyecto")
                || text.contains("no está configurada")) return ReviewReasonCode.SOURCE_NOT_CONFIGURED;
        if (text.contains("varias coincidencias")) return ReviewReasonCode.MULTIPLE_MATCHES;
        if (text.contains("búsqueda terminó antes") || text.contains("búsqueda incompleta"))
            return ReviewReasonCode.SEARCH_INCOMPLETE;
        if (text.contains("inconsistente") || text.contains("no devolvió un snapshot")
                || text.contains("no conserva el id") || text.contains("identidad")
                || text.contains("respuesta no coincide")) return ReviewReasonCode.SOURCE_DATA_INVALID;
        if (text.contains("no hay una regla") || text.contains("no identifica un campo")
                || text.contains("no tiene una configuración válida")
                || text.contains("no identifica qué") || text.contains("campos citados"))
            return ReviewReasonCode.RULE_NOT_DEFINED;
        if (text.contains("propuesta ia") || text.contains("gemini")) return ReviewReasonCode.AI_INCONCLUSIVE;
        if (text.contains("no se pudo acceder") || text.contains("no se pudo consultar") || text.contains("no se pudo confirmar")
                || text.contains("no se pudo completar") || text.contains("falló")
                || text.contains("no respondió") || text.contains("rechazó")
                || text.contains("permiso")) return ReviewReasonCode.SOURCE_UNAVAILABLE;
        return ReviewReasonCode.OTHER;
    }

    static EvidenceSearchStatus searchStatus(VerificationResult result, ReviewReasonCode reason) {
        ResultStatus evaluated = evaluationStatus(result);
        if (evaluated == ResultStatus.NOT_APPLICABLE
                || reason == ReviewReasonCode.APPLICABILITY_PENDING
                || reason == ReviewReasonCode.MISSING_TICKET_SELECTION
                || reason == ReviewReasonCode.SOURCE_NOT_CONFIGURED
                || reason == ReviewReasonCode.UNSUPPORTED_SOURCE) return EvidenceSearchStatus.NOT_ATTEMPTED;
        if (reason == ReviewReasonCode.SOURCE_UNAVAILABLE
                || reason == ReviewReasonCode.SOURCE_DATA_INVALID
                || reason == ReviewReasonCode.SEARCH_INCOMPLETE) return EvidenceSearchStatus.INCOMPLETE;
        if (!result.evidence().isEmpty()) return EvidenceSearchStatus.FOUND;
        if (evaluated == ResultStatus.DOES_NOT_COMPLY) return EvidenceSearchStatus.NOT_FOUND;
        return EvidenceSearchStatus.UNKNOWN;
    }

    private static ResultStatus evaluationStatus(VerificationResult result) {
        boolean applicabilityResolved = result.humanDecisions().stream()
                .anyMatch(decision -> decision.decisionType() == HumanDecisionType.APPLICABILITY);
        boolean statusOverridden = result.humanDecisions().stream()
                .anyMatch(decision -> decision.decisionType() == HumanDecisionType.RESULT_STATUS);
        return applicabilityResolved && !statusOverridden ? result.effectiveStatus() : result.proposedStatus();
    }
}
