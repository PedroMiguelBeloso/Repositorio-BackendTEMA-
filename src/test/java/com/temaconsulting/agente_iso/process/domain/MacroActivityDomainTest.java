package com.temaconsulting.agente_iso.process.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MacroActivityDomainTest {
    @Test
    void exposesExpectationsInVerificationOrder() {
        UUID versionId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        UUID activityLaterId = UUID.randomUUID();
        UUID activityFirstId = UUID.randomUUID();

        EvidenceExpectation input = expectation(macroId, null, "input", EvidenceLocation.INPUT, 0);
        EvidenceExpectation recordFirst = expectation(macroId, activityFirstId, "record-first", EvidenceLocation.ACTIVITY_RECORD, 1);
        EvidenceExpectation recordLater = expectation(macroId, activityLaterId, "record-later", EvidenceLocation.ACTIVITY_RECORD, 0);
        EvidenceExpectation output = expectation(macroId, null, "output", EvidenceLocation.OUTPUT, 0);

        Activity later = new Activity(activityLaterId, macroId, "later", null, "instructions", null, 10, List.of(recordLater));
        Activity first = new Activity(activityFirstId, macroId, "first", null, "instructions", null, 1, List.of(recordFirst));
        MacroActivity macro = new MacroActivity(
                macroId, versionId, "macro", null, 0,
                List.of(later, first), List.of(input), List.of(output));

        assertThat(macro.expectationsInVerificationOrder())
                .extracting(EvidenceExpectation::displayName)
                .containsExactly("input", "record-first", "record-later", "output");
    }

    private EvidenceExpectation expectation(
            UUID macroId,
            UUID activityId,
            String name,
            EvidenceLocation location,
            int order
    ) {
        return new EvidenceExpectation(
                UUID.randomUUID(), macroId, activityId, UUID.randomUUID(), name,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, location, order,
                EvidenceType.DOCUMENT, null
        );
    }
}
