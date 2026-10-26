package com.temaconsulting.agente_iso.service.application;

import com.temaconsulting.agente_iso.service.application.port.ServiceRepository;
import com.temaconsulting.agente_iso.service.application.port.ServiceSourceResolver;
import com.temaconsulting.agente_iso.service.application.model.ServiceView;
import com.temaconsulting.agente_iso.service.application.model.ServiceWriteCommand;

import com.temaconsulting.agente_iso.process.application.model.ProcessAssignmentInfo;
import com.temaconsulting.agente_iso.process.application.port.ProcessAssignmentReader;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.service.domain.Service;
import com.temaconsulting.agente_iso.service.domain.ServiceProcessAssignment;
import com.temaconsulting.agente_iso.service.domain.ServiceSources;
import com.temaconsulting.agente_iso.service.domain.error.ServiceValidationException;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@org.springframework.stereotype.Service
public class ServiceConfigurationService {
    private static final Logger log = LoggerFactory.getLogger(ServiceConfigurationService.class);
    private final ServiceRepository services;
    private final ProcessAssignmentReader processes;
    private final ObjectProvider<ServiceSourceResolver> sourceResolvers;
    private final ServiceQueryService queries;
    private final Clock clock;

    public ServiceConfigurationService(
            ServiceRepository services,
            ProcessAssignmentReader processes,
            ObjectProvider<ServiceSourceResolver> sourceResolvers,
            ServiceQueryService queries,
            Clock clock
    ) {
        this.services = services;
        this.processes = processes;
        this.sourceResolvers = sourceResolvers;
        this.queries = queries;
        this.clock = clock;
    }

    public ServiceView create(ServiceWriteCommand command) {
        if (command == null) {
            throw ServiceErrors.invalid("body", "Request body is required");
        }
        if (command.revision() != null) {
            throw ServiceErrors.invalid("revision", "Revision must be omitted when creating a service");
        }
        List<ServiceProcessAssignment> assignments = resolveAssignments(command.processVersionIds());
        ServiceSources sources = resolveSources(command.driveFolderId(), command.redmineProjectId());
        try {
            Service service = Service.create(
                    UUID.randomUUID(), command.name(), command.client(), command.serviceType(),
                    command.status(), assignments, sources, clock.instant()
            );
            services.insert(service);
            log.info("service_created serviceId={} processAssignments={}",
                    service.id(), assignments.size());
            return queries.get(service.id());
        } catch (ServiceValidationException ex) {
            throw ServiceErrors.invalid(ex.field(), ex.getMessage());
        }
    }

    public ServiceView update(UUID id, ServiceWriteCommand command) {
        if (command == null) {
            throw ServiceErrors.invalid("body", "Request body is required");
        }
        if (command.revision() == null) {
            throw ServiceErrors.invalid("revision", "Revision is required when updating a service");
        }
        Service previous = services.findById(id)
                .orElseThrow(() -> ServiceErrors.missing("Service not found: " + id));
        if (previous.revision() != command.revision()) {
            throw ServiceErrors.stale();
        }
        List<ServiceProcessAssignment> assignments = resolveAssignments(command.processVersionIds());
        ServiceSources sources = resolveSources(command.driveFolderId(), command.redmineProjectId());
        try {
            Service next = previous.replace(
                    command.name(), command.client(), command.serviceType(), command.status(),
                    assignments, sources, clock.instant()
            );
            if (!services.replace(next, command.revision())) {
                throw ServiceErrors.stale();
            }
            log.info("service_updated serviceId={} revision={} processAssignments={}",
                    id, next.revision(), assignments.size());
            return queries.get(id);
        } catch (ServiceValidationException ex) {
            throw ServiceErrors.invalid(ex.field(), ex.getMessage());
        }
    }

    private List<ServiceProcessAssignment> resolveAssignments(List<UUID> versionIds) {
        if (versionIds == null) {
            throw ServiceErrors.invalid("processVersionIds", "Process versions are required");
        }
        List<ServiceProcessAssignment> assignments = new ArrayList<>();
        Set<UUID> seenVersions = new HashSet<>();
        Set<UUID> seenProcesses = new HashSet<>();
        for (UUID versionId : versionIds) {
            if (versionId == null) {
                throw ServiceErrors.invalid("processVersionIds", "Process version ID cannot be null");
            }
            if (!seenVersions.add(versionId)) {
                throw ServiceErrors.rule("processVersionIds", "Process version is repeated: " + versionId);
            }
            ProcessAssignmentInfo info;
            try {
                info = processes.get(versionId);
            } catch (ApiException ex) {
                if (ex.httpStatus().value() == 404) {
                    throw ServiceErrors.rule("processVersionIds", "Process version does not exist: " + versionId);
                }
                throw ex;
            }
            if (info.status() != ProcessStatus.PUBLISHED) {
                throw ServiceErrors.rule("processVersionIds", "Only published process versions may be assigned");
            }
            if (info.code() == ProcessCode.QUALITY_CONTINUOUS_IMPROVEMENT) {
                throw ServiceErrors.rule("processVersionIds", "Quality and Continuous Improvement is not assigned to services");
            }
            if (!seenProcesses.add(info.processId())) {
                throw ServiceErrors.rule("processVersionIds", "Only one version of each process may be assigned");
            }
            assignments.add(new ServiceProcessAssignment(info.processId(), info.processVersionId()));
        }
        return List.copyOf(assignments);
    }

    private ServiceSources resolveSources(String driveFolderId, String redmineProjectId) {
        String driveId = optionalId(driveFolderId, "driveFolderId");
        String redmineId = optionalId(redmineProjectId, "redmineProjectId");
        if (driveId == null && redmineId == null) {
            return new ServiceSources(null, null);
        }
        ServiceSourceResolver resolver = sourceResolvers.getIfAvailable();
        if (resolver == null) {
            throw ServiceErrors.sourceUnavailable("Drive/Redmine source validation is not configured");
        }
        ExternalSource drive = driveId == null ? null : resolver.resolveDriveFolder(driveId);
        ExternalSource redmine = redmineId == null ? null : resolver.resolveRedmineProject(redmineId);
        if (driveId != null && drive == null || redmineId != null && redmine == null) {
            throw ServiceErrors.rule("sources", "The selected source is not accessible");
        }
        return new ServiceSources(drive, redmine);
    }

    private String optionalId(String value, String field) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw ServiceErrors.invalid(field, "Source ID cannot be blank");
        }
        return value.trim();
    }
}
