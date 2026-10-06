package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketSelectionPlannerDomainTest {
    private final TicketSelectionPlanner planner = new TicketSelectionPlanner();

    @Test
    void plansOneGroupFromTicketInputAndAllTicketContextExpectations() {
        UUID macroId = UUID.randomUUID();
        EvidenceExpectation input = ticket(macroId, null, EvidenceLocation.INPUT, Cardinality.ONE);
        EvidenceExpectation output = ticket(macroId, null, EvidenceLocation.OUTPUT, Cardinality.ONE);
        EvidenceExpectation document = new EvidenceExpectation(
                UUID.randomUUID(), macroId, null, UUID.randomUUID(), "document",
                ApplicabilityMode.CASE_DEPENDENT, Cardinality.ONE, EvidenceLocation.OUTPUT, 2,
                EvidenceType.DOCUMENT, null
        );
        EvidenceExpectation alwaysTool = new EvidenceExpectation(
                UUID.randomUUID(), macroId, null, UUID.randomUUID(), "tool",
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.OUTPUT, 3,
                EvidenceType.TOOL_RECORD, null
        );
        MacroActivity macro = macro(macroId, List.of(input), List.of(output, document, alwaysTool));

        var group = planner.plan(macro).orElseThrow();

        assertThat(group.id()).isEqualTo("ticket-group:" + macroId);
        assertThat(group.inputExpectationIds()).containsExactly(input.id());
        assertThat(group.relatedExpectationIds()).containsExactly(input.id(), output.id(), document.id());
        assertThat(group.cardinality()).isEqualTo(Cardinality.ONE);
    }

    @Test
    void returnsEmptyForMacroactivityWithoutTickets() {
        UUID macroId = UUID.randomUUID();
        MacroActivity macro = macro(macroId, List.of(document(macroId)), List.of());

        assertThat(planner.plan(macro)).isEmpty();
    }

    @Test
    void rejectsMoreThanOneTicketInput() {
        UUID macroId = UUID.randomUUID();
        EvidenceExpectation first = ticket(macroId, null, EvidenceLocation.INPUT, Cardinality.ONE);
        EvidenceExpectation second = ticket(macroId, null, EvidenceLocation.INPUT, Cardinality.ONE);

        assertThatThrownBy(() -> planner.plan(macro(macroId, List.of(first, second), List.of())))
                .isInstanceOf(TicketSelectionRuleViolation.class)
                .hasMessageContaining("more than one TICKET input");
    }

    @Test
    void rejectsDifferentTicketCardinalities() {
        UUID macroId = UUID.randomUUID();
        EvidenceExpectation first = ticket(macroId, null, EvidenceLocation.INPUT, Cardinality.ONE);
        EvidenceExpectation second = ticket(macroId, null, EvidenceLocation.OUTPUT, Cardinality.MULTIPLE);

        assertThatThrownBy(() -> planner.plan(macro(macroId, List.of(first), List.of(second))))
                .isInstanceOf(TicketSelectionRuleViolation.class)
                .hasMessageContaining("same cardinality");
    }

    private MacroActivity macro(UUID id, List<EvidenceExpectation> inputs, List<EvidenceExpectation> outputs) {
        return new MacroActivity(id, UUID.randomUUID(), "Macro", null, 0, List.of(), inputs, outputs);
    }

    private EvidenceExpectation ticket(UUID macroId, UUID activityId, EvidenceLocation location, Cardinality cardinality) {
        return new EvidenceExpectation(
                UUID.randomUUID(), macroId, activityId, UUID.randomUUID(), "ticket",
                ApplicabilityMode.ALWAYS, cardinality, location,
                0, EvidenceType.TICKET, null
        );
    }

    private EvidenceExpectation document(UUID macroId) {
        return new EvidenceExpectation(
                UUID.randomUUID(), macroId, null, UUID.randomUUID(), "document",
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT,
                0, EvidenceType.DOCUMENT, null
        );
    }
}
