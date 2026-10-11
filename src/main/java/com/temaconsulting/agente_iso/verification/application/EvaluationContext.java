package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EvaluationContext(
        UUID expectationId,
        String evidenceName,
        ApplicabilityMode applicability,
        String serviceType,
        List<String> policies,
        List<String> instructions,
        List<AiApplicabilityRequest.Fact> facts
) {
    public EvaluationContext {
        Objects.requireNonNull(expectationId, "expectationId is required");
        evidenceName = requireText(evidenceName, "evidenceName");
        applicability = Objects.requireNonNull(applicability, "applicability is required");
        serviceType = requireText(serviceType, "serviceType");
        policies = policies == null ? List.of() : List.copyOf(policies);
        instructions = instructions == null ? List.of() : List.copyOf(instructions);
        facts = facts == null ? List.of() : List.copyOf(facts);
    }

    public static EvaluationContext of(
            UUID expectationId,
            String evidenceName,
            ApplicabilityMode applicability,
            String serviceType,
            List<String> policies,
            List<String> instructions,
            List<AiApplicabilityRequest.Fact> facts
    ) {
        return new EvaluationContext(expectationId, evidenceName, applicability, serviceType, policies, instructions, facts);
    }

    public static EvaluationContext of(
            UUID expectationId,
            String evidenceName,
            ApplicabilityMode applicability,
            String serviceType,
            List<AiApplicabilityRequest.Fact> facts
    ) {
        return new EvaluationContext(expectationId, evidenceName, applicability, serviceType, List.of(), List.of(), facts);
    }

    public AiApplicabilityRequest toAiRequest() {
        if (applicability != ApplicabilityMode.CASE_DEPENDENT) {
            throw new IllegalStateException("AI applicability is only valid for CASE_DEPENDENT evidence");
        }
        return new AiApplicabilityRequest(
                expectationId,
                evidenceName,
                applicability,
                serviceType,
                policies,
                instructions,
                facts
        );
    }

    public List<AiApplicabilityRequest.Fact> facts() {
        return List.copyOf(facts);
    }

    public List<String> policies() {
        return List.copyOf(policies);
    }

    public List<String> instructions() {
        return List.copyOf(instructions);
    }

    public List<String> factValues() {
        return facts.stream().map(AiApplicabilityRequest.Fact::value).toList();
    }

    public static List<AiApplicabilityRequest.Fact> factsOf(String reference, String label, String value) {
        List<AiApplicabilityRequest.Fact> facts = new ArrayList<>();
        facts.add(new AiApplicabilityRequest.Fact(reference, label, value));
        return facts;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
