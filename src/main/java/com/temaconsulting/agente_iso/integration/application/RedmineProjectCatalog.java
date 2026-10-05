package com.temaconsulting.agente_iso.integration.application;

import com.temaconsulting.agente_iso.integration.domain.ExternalProject;
import com.temaconsulting.agente_iso.shared.application.Page;

public interface RedmineProjectCatalog {
    Page<ExternalProject> listProjects(int page, int size);

    Page<ExternalProject> searchProjects(String query, int page, int size);

    ExternalProject findById(String projectId);
}
