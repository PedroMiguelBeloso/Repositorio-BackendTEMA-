package com.temaconsulting.agente_iso.integration.infrastructure;

import com.temaconsulting.agente_iso.integration.application.IntegrationProbe;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import org.springframework.stereotype.Component;

/** Replaced by the read-only Redmine adapter in WBS 6.2.1. */
@Component
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
        throw new IllegalStateException("Redmine connector has not been implemented");
    }
}
