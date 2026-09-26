package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.api.dto.CreateVersionRequest;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.shared.application.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

import static com.temaconsulting.agente_iso.process.application.ProcessService.conflict;
import static com.temaconsulting.agente_iso.process.application.ProcessService.notFound;
import static com.temaconsulting.agente_iso.process.application.ProcessService.validationError;

@Service
public class ProcessDraftService {

    private final ProcessRepository processes;
    private final ProcessVersionRepository versions;
    private final Clock clock;

    public ProcessDraftService(
            ProcessRepository processes,
            ProcessVersionRepository versions,
            Clock clock
    ) {
        this.processes = processes;
        this.versions = versions;
        this.clock = clock;
    }

    public Page<ProcessVersion> listVersions(UUID processId, int page, int size) {
        requireExistingProcess(processId);
        validatePagination(page, size);
        return versions.listByProcess(processId, page, size);
    }

    public ProcessVersion getVersion(UUID processId, UUID versionId) {
        requireExistingProcess(processId);

        ProcessVersion version = versions.findWithTree(versionId)
                .orElseThrow(() -> notFound("Process version not found: " + versionId));

        if (!version.processId().equals(processId)) {
            throw notFound("Process version not found: " + versionId);
        }

        return version;
    }

    @Transactional
    public ProcessVersion createDraft(UUID processId, CreateVersionRequest request) {
        requireExistingProcess(processId);

        if (request == null) {
            throw validationError("request", "Request body is required");
        }

        String officialVersion = requireText(
                request.officialVersion(),
                "officialVersion",
                "Official version is required"
        );

        if (request.versionDate() == null) {
            throw validationError("versionDate", "Version date is required");
        }

        if (request.copyFromVersionId() != null) {
            throw conflict("copyFromVersionId is not supported by work package 5.1.1");
        }

        if (versions.findByOfficialVersion(processId, officialVersion).isPresent()) {
            throw conflict("Official version already exists for this process: " + officialVersion);
        }

        ProcessVersion draft = new ProcessVersion(
                UUID.randomUUID(),
                processId,
                officialVersion,
                request.versionDate(),
                ProcessStatus.DRAFT,
                0,
                "",
                "",
                null,
                clock.instant()
        );

        return versions.insertDraft(draft);
    }

    private void requireExistingProcess(UUID processId) {
        if (processId == null || processes.findById(processId).isEmpty()) {
            throw notFound("Process not found: " + processId);
        }
    }

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw validationError("page", "Page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw validationError("size", "Size must be between 1 and 100");
        }
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw validationError(field, message);
        }
        return value.trim();
    }
}
