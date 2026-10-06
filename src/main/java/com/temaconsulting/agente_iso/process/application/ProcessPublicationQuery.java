package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProcessPublicationQuery implements PublishedProcessReader {

    private final ProcessVersionRepository versions;

    public ProcessPublicationQuery(ProcessVersionRepository versions) {
        this.versions = versions;
    }

    @Override
    public ProcessVersion getPublished(UUID versionId) {
        ProcessVersion version = versions.findWithTree(versionId)
                .orElseThrow(() -> new ProcessVersionNotFoundException(versionId));

        if (version.status() != ProcessStatus.PUBLISHED) {
            throw new ProcessVersionNotPublishedException(versionId);
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
