package com.temaconsulting.agente_iso.integration.application.port;

import com.temaconsulting.agente_iso.integration.domain.ExternalProject;
import com.temaconsulting.agente_iso.shared.application.model.Page;

public interface RedmineProjectCatalog {
    Page<ExternalProject> listProjects(int page, int size);

    Page<ExternalProject> searchProjects(String query, int page, int size);

    ExternalProject findById(String projectId);
}
