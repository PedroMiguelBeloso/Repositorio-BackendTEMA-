package com.temaconsulting.agente_iso.integration.infrastructure;

import com.temaconsulting.agente_iso.integration.application.DriveFolderCatalog;
import com.temaconsulting.agente_iso.integration.domain.ExternalFolder;
import com.temaconsulting.agente_iso.service.application.ServiceSourceResolver;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** Single cross-source adapter. Redmine resolution is added in WBS 6.2.1. */
@Component
public class IntegrationServiceSourceResolver implements ServiceSourceResolver {
    private final DriveFolderCatalog drive;

    public IntegrationServiceSourceResolver(DriveFolderCatalog drive) {
        this.drive = drive;
    }

    @Override
    public ExternalSource resolveDriveFolder(String folderId) {
        ExternalFolder folder = drive.findById(folderId);
        return folder == null ? null : new ExternalSource(folder.id(), folder.name());
    }

    @Override
    public ExternalSource resolveRedmineProject(String projectId) {
        throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                "La conexión de Redmine todavía no está configurada", List.of());
    }
}
