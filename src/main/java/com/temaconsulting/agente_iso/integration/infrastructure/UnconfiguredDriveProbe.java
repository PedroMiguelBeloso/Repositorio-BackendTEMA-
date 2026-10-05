package com.temaconsulting.agente_iso.integration.infrastructure;

import com.temaconsulting.agente_iso.integration.application.IntegrationProbe;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import org.springframework.stereotype.Component;

/** Replaced by the read-only Drive adapter in WBS 6.1.1. */
@Component
public class UnconfiguredDriveProbe implements IntegrationProbe {
    @Override
    public IntegrationSource source() {
        return IntegrationSource.DRIVE;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void checkReadable() {
        throw new IllegalStateException("Drive connector has not been implemented");
    }
}
