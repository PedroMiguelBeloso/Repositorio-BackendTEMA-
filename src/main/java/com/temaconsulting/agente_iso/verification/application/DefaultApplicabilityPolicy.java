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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class DefaultApplicabilityPolicy implements ApplicabilityPolicy {
    private static final String[] APPLIES_TOKENS = {
            "desarrollo", "configuracion", "configuración", "correccion", "corrección",
            "ajuste", "modificacion", "implementacion", "implementación", "mantenimiento",
            "cambio", "reparacion", "reparación", "config", "desarrollar"
    };
    private static final String[] NOT_APPLICABLE_TOKENS = {
            "no hubo", "sin desarrollo", "sin configuracion", "sin configuración",
            "sin correccion", "sin corrección", "no se realizo", "no se realizó",
            "no tuvo desarrollo", "no hubo desarrollo", "no hubo configuracion",
            "no corresponde", "no aplica", "no requiere", "no es necesario",
            "no se requiere", "no se registró", "no se registro"
    };

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

        Optional<ApplicabilityAssessment> direct = evaluateStructuredFacts(context);
        if (direct.isPresent()) {
            return direct.get();
        }
        if (aiPort == null) {
            return ApplicabilityAssessment.notEvaluated(
                    "No se encontraron condiciones estructuradas suficientes y no hay una integración de IA configurada.",
                    "Faltan datos estructurados y la IA de aplicabilidad no está disponible."
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
                    "La IA concluyó que la evidencia aplica al caso.",
                    ai.limitations(),
                    DecisionOrigin.AI_APPLICABILITY
            );
            case NOT_APPLICABLE -> new Variance(
                    ApplicabilityDecision.NOT_APPLICABLE,
                    "La IA concluyó que la evidencia no aplica al caso.",
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

    private Optional<ApplicabilityAssessment> evaluateStructuredFacts(EvaluationContext context) {
        List<String> factTexts = context.facts().stream()
                .map(fact -> fact.label() + " " + fact.value())
                .map(DefaultApplicabilityPolicy::normalize)
                .toList();
        if (factTexts.isEmpty()) {
            return Optional.empty();
        }

        String joined = String.join(" ", factTexts);
        boolean positive = containsAny(joined, APPLIES_TOKENS);
        boolean negative = containsAny(joined, NOT_APPLICABLE_TOKENS);

        if (positive && !negative) {
            return Optional.of(ApplicabilityAssessment.deterministic(
                    ApplicabilityDecision.APPLIES,
                    "Los hechos autorizados del caso describen un escenario que coincide con la condición de aplicabilidad de la evidencia."
            ));
        }
        if (negative && !positive) {
            return Optional.of(ApplicabilityAssessment.deterministic(
                    ApplicabilityDecision.NOT_APPLICABLE,
                    "Los hechos autorizados del caso explican que la condición para exigir la evidencia no se dio."
            ));
        }
        if (positive && negative) {
            return Optional.of(ApplicabilityAssessment.notEvaluated(
                    "La evidencia presenta señales contradictorias en los hechos autorizados; requiere revisión humana.",
                    "Se detectaron indicadores tanto positivos como negativos dentro del mismo caso."
            ));
        }
        return Optional.empty();
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            String normalizedToken = normalize(token);
            if (text.contains(normalizedToken)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private record Variance(
            ApplicabilityDecision decision,
            String rationale,
            List<String> limitations,
            DecisionOrigin origin
    ) {
    }
}
