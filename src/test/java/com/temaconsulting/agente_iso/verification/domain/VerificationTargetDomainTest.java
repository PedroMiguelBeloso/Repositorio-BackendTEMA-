package com.temaconsulting.agente_iso.verification.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationTargetDomainTest {
    private final UUID serviceId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID macroId = UUID.randomUUID();

    @Test
    void requiresMacroActivityForMacroActivityScope() {
        assertThatThrownBy(() -> new VerificationTarget(
                serviceId, versionId, VerificationScope.MACRO_ACTIVITY, null, null))
                .isInstanceOf(VerificationDomainValidationException.class)
                .hasMessageContaining("required when scope is MACRO_ACTIVITY");
    }

    @Test
    void forbidsMacroActivityForProcessScope() {
        assertThatThrownBy(() -> new VerificationTarget(
                serviceId, versionId, VerificationScope.PROCESS, macroId, null))
                .isInstanceOf(VerificationDomainValidationException.class)
                .hasMessageContaining("must be omitted when scope is PROCESS");
    }

    @Test
    void trimsNoteAndKeepsValidTarget() {
        VerificationTarget target = new VerificationTarget(
                serviceId, versionId, VerificationScope.MACRO_ACTIVITY, macroId, "  note  ");

        assertThat(target.note()).isEqualTo("note");
    }
}
