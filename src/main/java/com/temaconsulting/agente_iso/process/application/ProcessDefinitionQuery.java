package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ProcessDefinitionQuery implements ProcessDefinitionReader {
    private final ProcessRepository processes;

    public ProcessDefinitionQuery(ProcessRepository processes) {
        this.processes = processes;
    }

    @Override
    public Optional<ProcessDefinition> find(UUID processId) {
        return processes.findById(processId);
    }
}
