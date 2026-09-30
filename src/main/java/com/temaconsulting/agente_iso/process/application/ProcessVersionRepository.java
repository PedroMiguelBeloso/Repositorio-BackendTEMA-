package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.shared.application.Page;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProcessVersionRepository {
    Optional<ProcessVersion> findWithTree(UUID id);

    Optional<ProcessVersion> findByOfficialVersion(UUID processId, String officialVersion);

    Page<ProcessVersion> listByProcess(UUID processId, int page, int size);

    ProcessVersion insertDraft(ProcessVersion version);

    ProcessVersion replaceDraft(ProcessVersion version, int expectedRevision);

    ProcessVersion publish(UUID id, int expectedRevision, Instant at);
}
