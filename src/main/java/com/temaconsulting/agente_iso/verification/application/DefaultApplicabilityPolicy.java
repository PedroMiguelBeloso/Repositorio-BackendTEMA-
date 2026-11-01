package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.port.ApplicabilityAiPort;
import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.model.EvaluationContext;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class DefaultApplicabilityPolicy implements ApplicabilityPolicy {
    private final ApplicabilityAiPort aiPort;

    public DefaultApplicabilityPolicy() {
        this(null);
    }

    @Autowired
    public DefaultApplicabilityPolicy(ApplicabilityAiPort aiPort) {
        this.aiPort = aiPort;
    }

    @Override
    public ApplicabilityAssessment assess(EvaluationContext context) {
        Objects.requireNonNull(context, "context is required");
        if (context.applicability() == ApplicabilityMode.ALWAYS) {
            return ApplicabilityAssessment.deterministic(
                    ApplicabilityDecision.APPLIES,
                    "La configuración publicada marca esta evidencia como siempre aplicable."
            );
        }
        if (context.applicability() != ApplicabilityMode.CASE_DEPENDENT) {
            return ApplicabilityAssessment.notEvaluated(
                    "El modo de aplicabilidad no es evaluable con esta política.",
                    "Modo de aplicabilidad no soportado por el evaluador."
            );
        }

        if (aiPort == null) {
            return ApplicabilityAssessment.notEvaluated(
                    "La aplicabilidad depende de los hechos del caso y no hay una integración de IA configurada.",
                    "La interpretación de los hechos requiere IA o una decisión humana."
            );
        }

        AiApplicabilityAssessment ai = aiPort.assess(context.toAiRequest());
        if (ai == null) {
            return ApplicabilityAssessment.notEvaluated(
                    "La propuesta de IA no fue recibida.",
                    "La integración de aplicabilidad devolvió un resultado nulo."
            );
        }

        Variance variance = switch (ai.applicability()) {
            case APPLIES -> new Variance(
                    ApplicabilityDecision.APPLIES,
                    ai.reason(),
                    ai.limitations(),
                    DecisionOrigin.AI_APPLICABILITY
            );
            case NOT_APPLICABLE -> new Variance(
                    ApplicabilityDecision.NOT_APPLICABLE,
                    ai.reason(),
                    ai.limitations(),
                    DecisionOrigin.AI_APPLICABILITY
            );
            case UNDETERMINED -> new Variance(
                    ApplicabilityDecision.UNDETERMINED,
                    ai.reason(),
                    ai.limitations().isEmpty() ? List.of("La IA no pudo resolver la aplicabilidad con los hechos autorizados.") : ai.limitations(),
                    DecisionOrigin.AI_APPLICABILITY
            );
        };

        return new ApplicabilityAssessment(
                variance.decision,
                variance.origin,
                variance.rationale,
                variance.limitations
        );
    }

    private record Variance(
            ApplicabilityDecision decision,
            String rationale,
            List<String> limitations,
            DecisionOrigin origin
    ) {
    }
}
