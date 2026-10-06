package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewExpectationDomainTest {
    private final UUID id = UUID.randomUUID();
    private final UUID macroId = UUID.randomUUID();

    @Test
    void requiresTicketGroupForTicketExpectations() {
        assertThatThrownBy(() -> new PreviewExpectation(
                id, macroId, null, EvidenceLocation.INPUT, "Ticket", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, null))
                .isInstanceOf(VerificationDomainValidationException.class)
                .hasMessageContaining("ticket selection group");
    }

    @Test
    void normalizesDisplayName() {
        PreviewExpectation expectation = new PreviewExpectation(
                id, macroId, null, EvidenceLocation.INPUT, "  Ticket  ", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, "group");

        assertThat(expectation.displayName()).isEqualTo("Ticket");
    }
}
