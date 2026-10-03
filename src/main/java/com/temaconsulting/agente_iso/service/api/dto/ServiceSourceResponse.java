package com.temaconsulting.agente_iso.service.api.dto;

public record ServiceSourceResponse(
        String driveFolderId, String driveFolderName,
        String redmineProjectId, String redmineProjectName
) {
}
