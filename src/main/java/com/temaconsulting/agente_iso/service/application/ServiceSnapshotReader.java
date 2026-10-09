package com.temaconsulting.agente_iso.service.application;

import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.service.domain.ServiceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only service boundary consumed by M2. It exposes only the data needed
 * to validate a verification target; it does not expose a concrete repository.
 */
public interface ServiceSnapshotReader {
    ServiceSnapshot get(UUID serviceId);

    Optional<ServiceSnapshot> find(UUID serviceId);

    record ServiceSnapshot(
            UUID serviceId,
            int revision,
            ServiceType serviceType,
            ExternalSource driveFolder,
            ExternalSource redmineProject,
            List<ProcessAssignment> processAssignments,
            String redmineProjectId
    ) {
        public ServiceSnapshot(
                UUID serviceId,
                int revision,
                List<ProcessAssignment> processAssignments,
                String redmineProjectId
        ) {
            this(serviceId, revision, null, null, null, processAssignments, redmineProjectId);
        }

        public ServiceSnapshot(
                UUID serviceId,
                int revision,
                ServiceType serviceType,
                ExternalSource driveFolder,
                ExternalSource redmineProject,
                List<ProcessAssignment> processAssignments
        ) {
            this(
                    serviceId,
                    revision,
                    serviceType,
                    driveFolder,
                    redmineProject,
                    processAssignments,
                    redmineProject == null ? null : redmineProject.id()
            );
        }

        public ServiceSnapshot {
            processAssignments = List.copyOf(processAssignments);
        }

        public ServiceSnapshot(UUID serviceId, int revision, List<ProcessAssignment> processAssignments) {
            this(serviceId, revision, null, null, null, processAssignments, null);
        }
    }

    record ProcessAssignment(UUID processId, UUID processVersionId) {
    }
}
