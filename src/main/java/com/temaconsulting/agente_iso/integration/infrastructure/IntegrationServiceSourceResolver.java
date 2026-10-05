package com.temaconsulting.agente_iso.integration.infrastructure;

import com.temaconsulting.agente_iso.integration.application.DriveFolderCatalog;
import com.temaconsulting.agente_iso.integration.application.RedmineProjectCatalog;
import com.temaconsulting.agente_iso.integration.domain.ExternalFolder;
import com.temaconsulting.agente_iso.integration.domain.ExternalProject;
import com.temaconsulting.agente_iso.service.application.ServiceSourceResolver;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class IntegrationServiceSourceResolver implements ServiceSourceResolver {
    private final DriveFolderCatalog drive;
    private final RedmineProjectCatalog redmine;

    public IntegrationServiceSourceResolver(DriveFolderCatalog drive, RedmineProjectCatalog redmine) {
        this.drive = drive;
        this.redmine = redmine;
    }

    @Override
    public ExternalSource resolveDriveFolder(String folderId) {
        ExternalFolder folder = drive.findById(folderId);
        return folder == null ? null : new ExternalSource(folder.id(), folder.name());
    }

    @Override
    public ExternalSource resolveRedmineProject(String projectId) {
        ExternalProject project = redmine.findById(projectId);
        return project == null ? null : new ExternalSource(project.id(), project.name());
    }
}
