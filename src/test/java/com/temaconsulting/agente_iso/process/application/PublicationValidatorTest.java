package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.application.model.Violation;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessPolicy;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationValidatorTest {

    private final PublicationValidator validator = new PublicationValidator();

    @Test
    void rejectsIncompleteDraft() {
        ProcessVersion draft = new ProcessVersion(
                UUID.randomUUID(), UUID.randomUUID(), "1.0", LocalDate.of(2026, 9, 30),
                ProcessStatus.DRAFT, 0, "", "", List.of(), List.of(), null,
                Instant.parse("2026-09-30T03:00:00Z")
        );

        assertThat(validator.validate(draft, List.of()))
                .extracting(Violation::field)
                .contains("objective", "owner", "macroActivities", "policies", "evidence");
    }

    @Test
    void rejectsDocumentWithoutOfficialNomenclature() {
        UUID registryId = UUID.randomUUID();
        ProcessVersion draft = validDraft(
                List.of(new EvidenceSpec(registryId, EvidenceLocation.INPUT, Cardinality.ONE)),
                List.of()
        );

        RegistryDefinition definition = new RegistryDefinition(
                registryId, "DOC-1", "Procedure", EvidenceType.DOCUMENT, null, true
        );

        assertThat(validator.validate(draft, List.of(definition)))
                .extracting(Violation::message)
                .anyMatch(message -> message.contains("nomenclature"));
    }

    @Test
    void rejectsMoreThanOneTicketInput() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ProcessVersion draft = validDraft(
                List.of(
                        new EvidenceSpec(first, EvidenceLocation.INPUT, Cardinality.ONE),
                        new EvidenceSpec(second, EvidenceLocation.INPUT, Cardinality.ONE)
                ),
                List.of()
        );

        assertThat(validator.validate(draft, List.of(ticketDefinition(first), ticketDefinition(second))))
                .extracting(Violation::message)
                .anyMatch(message -> message.contains("at most one TICKET input"));
    }

    @Test
    void rejectsDifferentTicketCardinalitiesWithinMacroactivity() {
        UUID inputRegistry = UUID.randomUUID();
        UUID outputRegistry = UUID.randomUUID();
        ProcessVersion draft = validDraft(
                List.of(new EvidenceSpec(inputRegistry, EvidenceLocation.INPUT, Cardinality.ONE)),
                List.of(new EvidenceSpec(outputRegistry, EvidenceLocation.OUTPUT, Cardinality.MULTIPLE))
        );

        assertThat(validator.validate(
                draft,
                List.of(ticketDefinition(inputRegistry), ticketDefinition(outputRegistry))
        ))
                .extracting(Violation::message)
                .anyMatch(message -> message.contains("same cardinality"));
    }

    @Test
    void acceptsValidDraft() {
        UUID registryId = UUID.randomUUID();
        ProcessVersion draft = validDraft(
                List.of(new EvidenceSpec(registryId, EvidenceLocation.INPUT, Cardinality.ONE)),
                List.of()
        );

        assertThat(validator.validate(draft, List.of(ticketDefinition(registryId)))).isEmpty();
    }

    @Test
    void rejectsVersionWithActivitiesButNoEvidence() {
        ProcessVersion draft = validDraft(List.of(), List.of());
        assertThat(validator.validate(draft, List.of()))
                .extracting(Violation::field)
                .contains("evidence");
    }

    @Test
    void rejectsVersionWithEvidenceButNoPolicy() {
        UUID registryId = UUID.randomUUID();
        ProcessVersion populated = validDraft(
                List.of(new EvidenceSpec(registryId, EvidenceLocation.INPUT, Cardinality.ONE)), List.of());
        ProcessVersion withoutPolicies = new ProcessVersion(populated.id(), populated.processId(),
                populated.officialVersion(), populated.versionDate(), populated.status(), populated.revision(),
                populated.objective(), populated.owner(), List.of(), populated.macroActivities(),
                populated.publishedAt(), populated.createdAt());

        assertThat(validator.validate(withoutPolicies, List.of(ticketDefinition(registryId))))
                .extracting(Violation::field)
                .contains("policies")
                .doesNotContain("evidence");
    }

    private static ProcessVersion validDraft(List<EvidenceSpec> inputs, List<EvidenceSpec> outputs) {
        UUID versionId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        List<EvidenceExpectation> mappedInputs = inputs.stream()
                .map(spec -> expectation(spec, macroId, null))
                .toList();
        List<EvidenceExpectation> mappedOutputs = outputs.stream()
                .map(spec -> expectation(spec, macroId, null))
                .toList();

        MacroActivity macro = new MacroActivity(
                macroId,
                versionId,
                "Macro",
                null,
                0,
                List.of(new Activity(
                        activityId,
                        macroId,
                        "Activity",
                        "Owner",
                        "Instructions",
                        null,
                        0,
                        List.of()
                )),
                mappedInputs,
                mappedOutputs
        );

        return new ProcessVersion(
                versionId,
                UUID.randomUUID(),
                "1.0",
                LocalDate.of(2026, 9, 30),
                ProcessStatus.DRAFT,
                2,
                "Objective",
                "Owner",
                List.of(new ProcessPolicy(UUID.randomUUID(), 0, "Policy")),
                List.of(macro),
                null,
                Instant.parse("2026-09-30T03:00:00Z")
        );
    }

    private static EvidenceExpectation expectation(EvidenceSpec spec, UUID macroId, UUID activityId) {
        return new EvidenceExpectation(
                UUID.randomUUID(),
                macroId,
                activityId,
                spec.registryId(),
                "Expected evidence",
                ApplicabilityMode.ALWAYS,
                spec.cardinality(),
                spec.location(),
                0
        );
    }

    private static RegistryDefinition ticketDefinition(UUID id) {
        return new RegistryDefinition(
                id,
                "TICKET-" + id,
                "Ticket",
                EvidenceType.TICKET,
                null,
                true
        );
    }

    private record EvidenceSpec(UUID registryId, EvidenceLocation location, Cardinality cardinality) {
    }
}
