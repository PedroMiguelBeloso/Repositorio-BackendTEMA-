package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Curated case context. The caller supplies facts from the authorized verification scope only. */
public record AiApplicabilityRequest(
        UUID expectationId,
        String evidenceName,
        ApplicabilityMode applicability,
        String serviceType,
        List<String> policies,
        List<String> instructions,
        List<Fact> facts
) {
    public AiApplicabilityRequest {
        Objects.requireNonNull(expectationId, "expectationId");
        evidenceName = required(evidenceName, "evidenceName");
        if (applicability != ApplicabilityMode.CASE_DEPENDENT) {
            throw new IllegalArgumentException("AI may assess only CASE_DEPENDENT expectations");
        }
        serviceType = required(serviceType, "serviceType");
        policies = List.copyOf(Objects.requireNonNull(policies, "policies"));
        instructions = List.copyOf(Objects.requireNonNull(instructions, "instructions"));
        facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
        if (policies.stream().anyMatch(value -> value == null || value.isBlank())
                || instructions.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Policies and instructions cannot contain blank entries");
        }
        if (new HashSet<>(facts.stream().map(Fact::reference).toList()).size() != facts.size()) {
            throw new IllegalArgumentException("Fact references must be unique");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    /** A fact can reference a selected ticket field, journal entry or another supplied case datum. */
    public record Fact(String reference, String label, String value) {
        public Fact {
            reference = required(reference, "reference");
            label = required(label, "label");
            value = required(value, "value");
        }
    }
}
