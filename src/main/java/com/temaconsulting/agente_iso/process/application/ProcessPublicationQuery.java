package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.temaconsulting.agente_iso.process.application.ProcessService.conflict;
import static com.temaconsulting.agente_iso.process.application.ProcessService.notFound;

@Service
public class ProcessPublicationQuery implements PublishedProcessReader {

    private final ProcessVersionRepository versions;

    public ProcessPublicationQuery(ProcessVersionRepository versions) {
        this.versions = versions;
    }

    @Override
    public ProcessVersion getPublished(UUID versionId) {
        ProcessVersion version = versions.findWithTree(versionId)
                .orElseThrow(() -> notFound("Process version not found: " + versionId));

        if (version.status() != ProcessStatus.PUBLISHED) {
            throw conflict("Process version is not published: " + versionId);
        }
        return version;
    }

    @Override
    public boolean isPublished(UUID versionId) {
        return versions.findWithTree(versionId)
                .map(version -> version.status() == ProcessStatus.PUBLISHED)
                .orElse(false);
    }
}
