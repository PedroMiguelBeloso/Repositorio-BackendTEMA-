package com.temaconsulting.agente_iso.process.application.port;

import com.temaconsulting.agente_iso.process.domain.ProcessVersion;

import java.util.UUID;

public interface PublishedProcessReader {

    ProcessVersion getPublished(UUID versionId);

    boolean isPublished(UUID versionId);
}
