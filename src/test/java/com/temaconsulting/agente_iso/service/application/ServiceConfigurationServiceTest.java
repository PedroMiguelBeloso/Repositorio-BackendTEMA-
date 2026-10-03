package com.temaconsulting.agente_iso.service.application;

import com.temaconsulting.agente_iso.process.application.ProcessAssignmentInfo;
import com.temaconsulting.agente_iso.process.application.ProcessAssignmentReader;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.service.domain.Service;
import com.temaconsulting.agente_iso.service.domain.ServiceStatus;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceConfigurationServiceTest {
    @Test
    void storesOnlySourcesResolvedByAdaptersAndTheSelectedPublishedVersion() {
        ServiceRepository repository = mock(ServiceRepository.class);
        ProcessAssignmentReader processes = mock(ProcessAssignmentReader.class);
        ServiceSourceResolver resolver = mock(ServiceSourceResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ServiceSourceResolver> provider = mock(ObjectProvider.class);
        ServiceQueryService queries = mock(ServiceQueryService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
        UUID versionId = UUID.randomUUID();
        UUID processId = UUID.randomUUID();
        when(processes.get(versionId)).thenReturn(new ProcessAssignmentInfo(
                versionId, processId, ProcessCode.START_UP, "Start Up", "2.0",
                ProcessStatus.PUBLISHED, true, List.of()
        ));
        when(provider.getIfAvailable()).thenReturn(resolver);
        when(resolver.resolveDriveFolder("folder-1")).thenReturn(new ExternalSource("folder-1", "Carpeta servicio"));
        when(resolver.resolveRedmineProject("project-2")).thenReturn(new ExternalSource("project-2", "Proyecto Redmine"));

        var configuration = new ServiceConfigurationService(repository, processes, provider, queries, clock);
        configuration.create(new ServiceWriteCommand(
                null, "Servicio de prueba", "Cliente", ServiceType.SUPPORT_ARIBA,
                ServiceStatus.ACTIVE, List.of(versionId), "folder-1", "project-2"
        ));

        var saved = org.mockito.ArgumentCaptor.forClass(Service.class);
        verify(repository).insert(saved.capture());
        Service service = saved.getValue();
        assertThat(service.processes()).hasSize(1);
        assertThat(service.processes().getFirst().processId()).isEqualTo(processId);
        assertThat(service.sources().driveFolder().name()).isEqualTo("Carpeta servicio");
        assertThat(service.sources().redmineProject().name()).isEqualTo("Proyecto Redmine");
        assertThat(service.revision()).isEqualTo(1);
    }
}
