package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.shared.application.Page;

import java.util.Optional;
import java.util.UUID;

public interface ProcessRepository {
    Optional<ProcessDefinition> findById(UUID id);

    Optional<ProcessDefinition> findByCode(ProcessCode code);

    Page<ProcessDefinition> list(int page, int size);

    ProcessDefinition insert(ProcessDefinition process);
}
