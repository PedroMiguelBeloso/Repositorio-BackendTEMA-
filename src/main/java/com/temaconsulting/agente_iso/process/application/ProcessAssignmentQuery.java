package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.temaconsulting.agente_iso.process.application.ProcessService.notFound;

@Service
public class ProcessAssignmentQuery implements ProcessAssignmentReader {
    private final ProcessVersionRepository versions;
    private final ProcessRepository processes;

    public ProcessAssignmentQuery(ProcessVersionRepository versions, ProcessRepository processes) {
        this.versions = versions;
        this.processes = processes;
    }

    @Override
    public ProcessAssignmentInfo get(UUID versionId) {
        ProcessVersion version = versions.findWithTree(versionId)
                .orElseThrow(() -> notFound("Process version not found: " + versionId));
        ProcessDefinition process = processes.findById(version.processId())
                .orElseThrow(() -> notFound("Process not found: " + version.processId()));
        return new ProcessAssignmentInfo(
                version.id(), process.id(), process.code(), process.name(),
                version.officialVersion(), version.status(), process.m2Enabled(),
                version.macroActivities().stream()
                        .map(macro -> new ProcessAssignmentInfo.MacroActivityInfo(macro.id(), macro.name()))
                        .toList()
        );
    }
}
