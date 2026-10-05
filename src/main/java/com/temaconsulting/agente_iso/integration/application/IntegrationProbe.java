package com.temaconsulting.agente_iso.integration.application;

import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;

/**
 * A source adapter must perform an actual read in {@link #checkReadable()}.
 * Returning normally is the only way the coordinator can report AVAILABLE.
 * Expected remote failures must be translated to SourceProbeException.
 */
public interface IntegrationProbe {
    IntegrationSource source();

    /** Configuration check only; this method must not contact the remote source. */
    boolean isConfigured();

    /** Read-only operation with interruptible, bounded network timeouts. */
    void checkReadable();
}
