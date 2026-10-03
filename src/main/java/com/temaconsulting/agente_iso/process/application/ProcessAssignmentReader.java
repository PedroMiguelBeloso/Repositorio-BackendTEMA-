package com.temaconsulting.agente_iso.process.application;

import java.util.UUID;

public interface ProcessAssignmentReader {
    ProcessAssignmentInfo get(UUID versionId);
}
