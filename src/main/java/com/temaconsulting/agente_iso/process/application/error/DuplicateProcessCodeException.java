package com.temaconsulting.agente_iso.process.application.error;

import com.temaconsulting.agente_iso.process.domain.ProcessCode;

public class DuplicateProcessCodeException extends RuntimeException {
    public DuplicateProcessCodeException(ProcessCode code) {
        super("Process code already exists: " + code.name());
    }
}
