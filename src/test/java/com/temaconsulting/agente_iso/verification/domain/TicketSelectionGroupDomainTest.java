package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.process.domain.Cardinality;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketSelectionGroupDomainTest {
    private final UUID macroId = UUID.randomUUID();
    private final UUID inputId = UUID.randomUUID();
    private final UUID relatedId = UUID.randomUUID();

    @Test
    void generatesStableIdFromMacroactivity() {
        TicketSelectionGroup first = TicketSelectionGroup.forMacroActivity(
                macroId, "Tickets - Macro", Cardinality.ONE,
                List.of(inputId), List.of(inputId, relatedId), true);
        TicketSelectionGroup second = TicketSelectionGroup.forMacroActivity(
                macroId, "Tickets - Macro", Cardinality.ONE,
                List.of(inputId), List.of(inputId, relatedId), true);

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first.id()).isEqualTo("ticket-group:" + macroId);
    }

    @Test
    void rejectsMoreThanOneInputExpectation() {
        assertThatThrownBy(() -> new TicketSelectionGroup(
                TicketSelectionGroup.stableIdFor(macroId), macroId, "Tickets", Cardinality.ONE,
                List.of(inputId, UUID.randomUUID()), List.of(inputId, relatedId), true))
                .isInstanceOf(TicketSelectionRuleViolation.class)
                .hasMessageContaining("at most one TICKET input");
    }

    @Test
    void rejectsInputNotPresentAmongRelatedExpectations() {
        assertThatThrownBy(() -> new TicketSelectionGroup(
                TicketSelectionGroup.stableIdFor(macroId), macroId, "Tickets", Cardinality.ONE,
                List.of(inputId), List.of(relatedId), true))
                .isInstanceOf(TicketSelectionRuleViolation.class)
                .hasMessageContaining("Every TICKET input expectation");
    }

    @Test
    void rejectsNonStableGroupId() {
        assertThatThrownBy(() -> new TicketSelectionGroup(
                "arbitrary-group", macroId, "Tickets", Cardinality.ONE,
                List.of(), List.of(relatedId), true))
                .isInstanceOf(VerificationDomainValidationException.class)
                .hasMessageContaining("stable ID");
    }
}
