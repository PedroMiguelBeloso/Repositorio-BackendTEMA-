package com.temaconsulting.agente_iso.process.domain;

public enum ProcessCode {
    START_UP(true),
    TAKE_OVER(true),
    SUPPORT_SOFTWARE_FACTORY(true),
    QUALITY_CONTINUOUS_IMPROVEMENT(false);

    private final boolean m2Enabled;

    ProcessCode(boolean m2Enabled) {
        this.m2Enabled = m2Enabled;
    }

    public boolean isM2Enabled() {
        return m2Enabled;
    }
}
