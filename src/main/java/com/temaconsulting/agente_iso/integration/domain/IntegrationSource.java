package com.temaconsulting.agente_iso.integration.domain;

public enum IntegrationSource {
    DRIVE("Google Drive"),
    REDMINE("Redmine"),
    GEMINI("Gemini");

    private final String displayName;

    IntegrationSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
