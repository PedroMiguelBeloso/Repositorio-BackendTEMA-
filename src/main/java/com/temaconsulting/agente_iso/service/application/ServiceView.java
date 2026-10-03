package com.temaconsulting.agente_iso.service.application;

import com.temaconsulting.agente_iso.process.application.ProcessAssignmentInfo;
import com.temaconsulting.agente_iso.service.domain.Service;

import java.util.List;

public record ServiceView(Service service, List<ProcessAssignmentInfo> processes) {
    public ServiceView {
        processes = List.copyOf(processes);
    }
}
