package com.temaconsulting.agente_iso.integration.application;

import com.temaconsulting.agente_iso.integration.domain.ExternalFolder;
import com.temaconsulting.agente_iso.shared.application.Page;

public interface DriveFolderCatalog {
    Page<ExternalFolder> search(String query, int page, int size);

    /** Returns null only when the ID does not identify an accessible, active folder. */
    ExternalFolder findById(String folderId);
}
