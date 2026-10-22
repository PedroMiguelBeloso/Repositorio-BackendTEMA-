package com.temaconsulting.agente_iso.service.application.model;

import com.temaconsulting.agente_iso.process.application.model.ProcessAssignmentInfo;
import com.temaconsulting.agente_iso.service.domain.Service;

import java.util.List;

public record ServiceView(Service service, List<ProcessAssignmentInfo> processes) {
    public ServiceView {
        processes = List.copyOf(processes);
    }
}
