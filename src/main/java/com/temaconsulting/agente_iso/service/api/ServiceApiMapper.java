package com.temaconsulting.agente_iso.service.api;

import com.temaconsulting.agente_iso.process.application.ProcessAssignmentInfo;
import com.temaconsulting.agente_iso.service.api.dto.ServiceProcessResponse;
import com.temaconsulting.agente_iso.service.api.dto.ServiceResponse;
import com.temaconsulting.agente_iso.service.api.dto.ServiceSourceResponse;
import com.temaconsulting.agente_iso.service.api.dto.VerifiableProcessResponse;
import com.temaconsulting.agente_iso.service.application.ServiceView;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.service.domain.Service;

final class ServiceApiMapper {
    private ServiceApiMapper() {
    }

    static ServiceResponse toResponse(ServiceView view) {
        Service service = view.service();
        return new ServiceResponse(
                service.id(), service.name(), service.client(), service.serviceType(), service.status(),
                service.revision(), view.processes().stream().map(ServiceApiMapper::toProcess).toList(),
                new ServiceSourceResponse(
                        id(service.sources().driveFolder()), name(service.sources().driveFolder()),
                        id(service.sources().redmineProject()), name(service.sources().redmineProject())
                ),
                service.createdAt(), service.updatedAt()
        );
    }

    static VerifiableProcessResponse toVerifiableProcess(ProcessAssignmentInfo info) {
        return new VerifiableProcessResponse(
                info.processVersionId(), info.processName(), info.officialVersion(),
                info.macroActivities().stream()
                        .map(macro -> new VerifiableProcessResponse.MacroActivityResponse(macro.id(), macro.name()))
                        .toList()
        );
    }

    private static ServiceProcessResponse toProcess(ProcessAssignmentInfo info) {
        return new ServiceProcessResponse(
                info.processVersionId(), info.processId(), info.processName(),
                info.officialVersion(), info.m2Enabled()
        );
    }

    private static String id(ExternalSource source) {
        return source == null ? null : source.id();
    }

    private static String name(ExternalSource source) {
        return source == null ? null : source.name();
    }
}
