package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.verification.domain.error.VerificationDomainValidationException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationPreviewDomainTest {
    @Test
    void processPreviewCannotCarryMacroActivityId() {
        assertThatThrownBy(() -> new VerificationPreview(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                VerificationScope.PROCESS,
                UUID.randomUUID(),
                List.of(),
                List.of()))
                .isInstanceOf(VerificationDomainValidationException.class)
                .hasMessageContaining("must be null for a PROCESS preview");
    }

    @Test
    void macroActivityPreviewCannotContainExpectationFromAnotherMacroactivity() {
        UUID selectedMacro = UUID.randomUUID();
        UUID foreignMacro = UUID.randomUUID();
        UUID expectationId = UUID.randomUUID();
        assertThatThrownBy(() -> new VerificationPreview(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                VerificationScope.MACRO_ACTIVITY,
                selectedMacro,
                List.of(new PreviewExpectation(
                        expectationId, foreignMacro, null,
                        com.temaconsulting.agente_iso.process.domain.EvidenceLocation.INPUT,
                        "document",
                        com.temaconsulting.agente_iso.process.domain.EvidenceType.DOCUMENT,
                        com.temaconsulting.agente_iso.process.domain.ApplicabilityMode.ALWAYS,
                        com.temaconsulting.agente_iso.process.domain.Cardinality.ONE,
                        null
                )),
                List.of()
        ))
                .isInstanceOf(VerificationDomainValidationException.class)
                .hasMessageContaining("selected macroactivity");
    }

    @Test
    void groupCannotReferenceExpectationOutsidePreview() {
        UUID macro = UUID.randomUUID();
        UUID inputId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();
        TicketSelectionGroup group = TicketSelectionGroup.forMacroActivity(
                macro, "Tickets",
                com.temaconsulting.agente_iso.process.domain.Cardinality.ONE,
                List.of(inputId), List.of(inputId, relatedId), true
        );
        assertThatThrownBy(() -> new VerificationPreview(
                UUID.randomUUID(), 1, UUID.randomUUID(), VerificationScope.MACRO_ACTIVITY, macro,
                List.of(new PreviewExpectation(
                        inputId, macro, null,
                        com.temaconsulting.agente_iso.process.domain.EvidenceLocation.INPUT,
                        "ticket",
                        com.temaconsulting.agente_iso.process.domain.EvidenceType.TICKET,
                        com.temaconsulting.agente_iso.process.domain.ApplicabilityMode.ALWAYS,
                        com.temaconsulting.agente_iso.process.domain.Cardinality.ONE, group.id()
                )),
                List.of(group)
        ))
                .isInstanceOf(VerificationDomainValidationException.class)
                .hasMessageContaining("outside the preview");
    }
}
