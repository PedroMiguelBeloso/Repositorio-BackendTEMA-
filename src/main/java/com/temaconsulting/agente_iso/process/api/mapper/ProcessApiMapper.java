package com.temaconsulting.agente_iso.process.api.mapper;

import com.temaconsulting.agente_iso.process.api.dto.ProcessDefinitionResponse;
import com.temaconsulting.agente_iso.process.api.dto.ProcessVersionResponse;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;

import java.util.List;

public final class ProcessApiMapper {

    private ProcessApiMapper() {
    }

    public static ProcessDefinitionResponse toResponse(ProcessDefinition process) {
        return new ProcessDefinitionResponse(
                process.id(),
                process.code(),
                process.name(),
                process.m2Enabled(),
                process.createdAt()
        );
    }

    public static ProcessVersionResponse toResponse(ProcessVersion version) {
        return new ProcessVersionResponse(
                version.id(),
                version.processId(),
                version.officialVersion(),
                version.versionDate(),
                version.status(),
                version.revision(),
                version.objective(),
                version.owner(),
                List.of(),
                List.of(),
                version.publishedAt(),
                version.createdAt()
        );
    }
}
