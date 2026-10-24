package com.temaconsulting.agente_iso.service.application;

import com.temaconsulting.agente_iso.service.application.port.ServiceRepository;
import com.temaconsulting.agente_iso.service.application.port.ServiceTicketSearch;
import com.temaconsulting.agente_iso.service.application.model.ServiceView;
import com.temaconsulting.agente_iso.service.application.model.TicketSummary;

import com.temaconsulting.agente_iso.process.application.model.ProcessAssignmentInfo;
import com.temaconsulting.agente_iso.process.application.port.ProcessAssignmentReader;
import com.temaconsulting.agente_iso.service.domain.Service;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class ServiceQueryService {
    private final ServiceRepository services;
    private final ProcessAssignmentReader processes;
    private final ObjectProvider<ServiceTicketSearch> ticketSearches;

    public ServiceQueryService(
            ServiceRepository services,
            ProcessAssignmentReader processes,
            ObjectProvider<ServiceTicketSearch> ticketSearches
    ) {
        this.services = services;
        this.processes = processes;
        this.ticketSearches = ticketSearches;
    }

    public Page<ServiceView> list(int page, int size) {
        validatePage(page, size);
        Page<Service> result = services.list(page, size);
        return new Page<>(result.items().stream().map(this::toView).toList(), page, size, result.totalElements());
    }

    public ServiceView get(UUID id) {
        Service service = services.findById(id)
                .orElseThrow(() -> ServiceErrors.missing("Service not found: " + id));
        return toView(service);
    }

    public List<ProcessAssignmentInfo> verifiableProcesses(UUID serviceId) {
        return get(serviceId).processes().stream().filter(ProcessAssignmentInfo::m2Enabled).toList();
    }

    public Page<TicketSummary> tickets(UUID serviceId, String query, int page, int size) {
        validatePage(page, size);
        Service service = services.findById(serviceId)
                .orElseThrow(() -> ServiceErrors.missing("Service not found: " + serviceId));
        if (service.sources().redmineProject() == null) {
            throw ServiceErrors.sourceUnavailable("No Redmine project is configured for this service");
        }
        ServiceTicketSearch search = ticketSearches.getIfAvailable();
        if (search == null) {
            throw ServiceErrors.sourceUnavailable("Redmine ticket search is not configured");
        }
        return search.search(service.sources().redmineProject().id(), query, page, size);
    }

    private ServiceView toView(Service service) {
        return new ServiceView(service, service.processes().stream()
                .map(assignment -> processes.get(assignment.processVersionId()))
                .toList());
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw ServiceErrors.invalid("page", "Page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw ServiceErrors.invalid("size", "Size must be between 1 and 100");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw ServiceErrors.invalid("page", "Page offset is too large");
        }
    }
}
