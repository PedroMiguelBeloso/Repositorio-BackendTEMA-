package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;

import java.util.Optional;
import java.util.UUID;

public interface ProcessDefinitionReader {
    Optional<ProcessDefinition> find(UUID processId);
}
