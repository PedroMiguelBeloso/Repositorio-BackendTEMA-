package com.temaconsulting.agente_iso.service.application.port;

import com.temaconsulting.agente_iso.service.domain.ExternalSource;

/** To be implemented by the Drive and Redmine adapters in their integration tasks. */
public interface ServiceSourceResolver {
    ExternalSource resolveDriveFolder(String folderId);

    ExternalSource resolveRedmineProject(String projectId);
}
