package com.temaconsulting.agente_iso.service.application;

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
            List<ProcessAssignment> processAssignments
    ) {
        public ServiceSnapshot {
            processAssignments = List.copyOf(processAssignments);
        }
    }

    record ProcessAssignment(UUID processId, UUID processVersionId) {
    }
}
