package com.temaconsulting.agente_iso.integration.application.error;

import com.temaconsulting.agente_iso.integration.domain.SourceHealth;

/** Only an explicitly supplied, sanitized diagnostic may reach the public response. */
public final class SourceProbeException extends RuntimeException {
    private final SourceHealth status;
    private final String safeMessage;

    public SourceProbeException(SourceHealth status, Throwable cause) {
        this(status, cause, null);
    }

    public SourceProbeException(SourceHealth status, Throwable cause, String safeMessage) {
        super(cause);
        if (status != SourceHealth.AUTH_ERROR
                && status != SourceHealth.PERMISSION_ERROR
                && status != SourceHealth.UNAVAILABLE) {
            throw new IllegalArgumentException("Invalid source failure status: " + status);
        }
        this.status = status;
        this.safeMessage = safeMessage;
    }

    public SourceHealth status() {
        return status;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
