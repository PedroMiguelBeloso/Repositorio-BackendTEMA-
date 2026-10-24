package com.temaconsulting.agente_iso.integration.infrastructure.redmine;

import com.temaconsulting.agente_iso.integration.application.port.IntegrationProbe;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;

/** Replaced by the real Redmine read-only adapter. */
public class UnconfiguredRedmineProbe implements IntegrationProbe {
    @Override
    public IntegrationSource source() {
        return IntegrationSource.REDMINE;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void checkReadable() {
        throw new IllegalStateException("Redmine connector is disabled");
    }
}
