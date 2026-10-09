package com.temaconsulting.agente_iso.service.application;

import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.service.domain.Service;
import com.temaconsulting.agente_iso.service.domain.ServiceProcessAssignment;
import com.temaconsulting.agente_iso.service.domain.ServiceSources;
import com.temaconsulting.agente_iso.service.domain.ServiceStatus;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceSnapshotQueryTest {

    @Test
    void exposesOnlyRevisionAndProcessVersionAssignmentsNeededByM2() {
        ServiceRepository repository = mock(ServiceRepository.class);
        UUID serviceId = UUID.randomUUID();
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-05T12:00:00Z");
        Service service = new Service(
                serviceId,
                "Servicio",
                "Cliente",
                ServiceType.SOFTWARE_FACTORY,
                ServiceStatus.ACTIVE,
                4,
                List.of(new ServiceProcessAssignment(processId, versionId)),
                new ServiceSources(
                        new ExternalSource("drive-1", "Drive"),
                        new ExternalSource("redmine-1", "Redmine")
                ),
                now,
                now
        );
        when(repository.findById(serviceId)).thenReturn(Optional.of(service));

        var snapshot = new ServiceSnapshotQuery(repository).get(serviceId);

        assertThat(snapshot.serviceId()).isEqualTo(serviceId);
        assertThat(snapshot.revision()).isEqualTo(4);
        assertThat(snapshot.redmineProjectId()).isEqualTo("redmine-1");
        assertThat(snapshot.processAssignments()).containsExactly(
                new ServiceSnapshotReader.ProcessAssignment(processId, versionId)
        );
    }
}
