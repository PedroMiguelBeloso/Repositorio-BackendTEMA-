package com.temaconsulting.agente_iso.integration.application;

import com.temaconsulting.agente_iso.integration.domain.SourceHealth;

/** Internal diagnostics are deliberately not returned in the public response. */
public final class SourceProbeException extends RuntimeException {
    private final SourceHealth status;

    public SourceProbeException(SourceHealth status, Throwable cause) {
        super(cause);
        if (status != SourceHealth.AUTH_ERROR
                && status != SourceHealth.PERMISSION_ERROR
                && status != SourceHealth.UNAVAILABLE) {
            throw new IllegalArgumentException("Invalid source failure status: " + status);
        }
        this.status = status;
    }

    public SourceHealth status() {
        return status;
    }
}
