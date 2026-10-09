package com.temaconsulting.agente_iso.service.application;

import com.temaconsulting.agente_iso.service.domain.Service;
import com.temaconsulting.agente_iso.service.domain.ServiceProcessAssignment;

import java.util.Optional;
import java.util.UUID;

import static com.temaconsulting.agente_iso.service.application.ServiceErrors.missing;

@org.springframework.stereotype.Service
public class ServiceSnapshotQuery implements ServiceSnapshotReader {
    private final ServiceRepository services;

    public ServiceSnapshotQuery(ServiceRepository services) {
        this.services = services;
    }

    @Override
    public ServiceSnapshot get(UUID serviceId) {
        return find(serviceId)
                .orElseThrow(() -> missing("Service not found: " + serviceId));
    }

    @Override
    public Optional<ServiceSnapshot> find(UUID serviceId) {
        return services.findById(serviceId).map(this::toSnapshot);
    }

    private ServiceSnapshot toSnapshot(Service service) {
        return new ServiceSnapshot(
                service.id(),
                service.revision(),
                service.serviceType(),
                service.sources().driveFolder(),
                service.sources().redmineProject(),
                service.processes().stream()
                        .map(this::toAssignment)
                        .toList(),
                service.sources().redmineProject() == null
                        ? null
                        : service.sources().redmineProject().id()
        );
    }

    private ProcessAssignment toAssignment(ServiceProcessAssignment assignment) {
        return new ProcessAssignment(assignment.processId(), assignment.processVersionId());
    }
}
