package com.temaconsulting.agente_iso.process.application.port;

import com.temaconsulting.agente_iso.process.application.model.ProcessAssignmentInfo;

import java.util.Optional;
import java.util.UUID;

public interface ProcessAssignmentReader {
    ProcessAssignmentInfo get(UUID versionId);

    Optional<ProcessAssignmentInfo> find(UUID versionId);
}
