package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityRequest;
import com.temaconsulting.agente_iso.verification.application.model.EvaluationContext;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultApplicabilityPolicyTest {
    private static final UUID EXPECTATION_ID = UUID.fromString("c1859862-7a3f-4660-80d6-3d0461805985");

    @Test
    void negativeFreeTextIsInterpretedForTheSpecificEvidenceInsteadOfMarkedContradictory() {
        AtomicInteger calls = new AtomicInteger();
        var policy = new DefaultApplicabilityPolicy(request -> {
            calls.incrementAndGet();
            assertThat(request.facts()).singleElement().satisfies(fact ->
                    assertThat(fact.value()).contains("desarrollo", "no corresponde"));
            return new AiApplicabilityAssessment(request.expectationId(),
                    ApplicabilityDecision.NOT_APPLICABLE, "No hay proyecto nuevo.",
                    List.of("case:note"), List.of(), "gemini-test", "response-test");
        });
        var context = context(ApplicabilityMode.CASE_DEPENDENT,
                "No habrá desarrollo de Ariba y no corresponde elaborar un Plan de Proyecto.");

        var assessment = policy.assess(context);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(assessment.decision()).isEqualTo(ApplicabilityDecision.NOT_APPLICABLE);
        assertThat(assessment.origin()).isEqualTo(DecisionOrigin.AI_APPLICABILITY);
        assertThat(assessment.rationale()).isEqualTo("No hay proyecto nuevo.");
    }

    @Test
    void alwaysApplicableBypassesAiEvenWithNegativeFreeText() {
        AtomicInteger calls = new AtomicInteger();
        var policy = new DefaultApplicabilityPolicy(request -> {
            calls.incrementAndGet();
            return AiApplicabilityAssessment.undetermined(request.expectationId(),
                    "No debería consultarse", "No debería consultarse", "gemini-test");
        });

        var assessment = policy.assess(context(ApplicabilityMode.ALWAYS, "No habrá desarrollo."));

        assertThat(calls.get()).isZero();
        assertThat(assessment.decision()).isEqualTo(ApplicabilityDecision.APPLIES);
        assertThat(assessment.origin()).isEqualTo(DecisionOrigin.DETERMINISTIC);
    }

    private static EvaluationContext context(ApplicabilityMode mode, String note) {
        return EvaluationContext.of(EXPECTATION_ID, "Plan de Proyecto (si aplica)", mode,
                "Soporte", List.of(), List.of(),
                List.of(new AiApplicabilityRequest.Fact("case:note", "Contexto del caso", note)));
    }
}
