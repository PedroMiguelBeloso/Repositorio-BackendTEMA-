package com.temaconsulting.agente_iso.process.application.error;

public class DuplicateOfficialVersionException extends RuntimeException {
    public DuplicateOfficialVersionException(String officialVersion) {
        super("Official version already exists: " + officialVersion);
    }
}
