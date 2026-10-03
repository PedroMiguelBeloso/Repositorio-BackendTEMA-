package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;

import java.util.List;
import java.util.UUID;

/** Read-only process information exposed to the service module. */
public record ProcessAssignmentInfo(
        UUID processVersionId,
        UUID processId,
        ProcessCode code,
        String processName,
        String officialVersion,
        ProcessStatus status,
        boolean m2Enabled,
        List<MacroActivityInfo> macroActivities
) {
    public ProcessAssignmentInfo {
        macroActivities = List.copyOf(macroActivities);
    }

    public record MacroActivityInfo(UUID id, String name) {
    }
}
