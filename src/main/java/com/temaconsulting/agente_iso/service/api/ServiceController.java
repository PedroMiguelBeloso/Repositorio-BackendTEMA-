package com.temaconsulting.agente_iso.service.api;

import com.temaconsulting.agente_iso.service.api.mapper.ServiceApiMapper;

import com.temaconsulting.agente_iso.service.api.dto.ServiceResponse;
import com.temaconsulting.agente_iso.service.api.dto.ServiceWriteRequest;
import com.temaconsulting.agente_iso.service.api.dto.VerifiableProcessResponse;
import com.temaconsulting.agente_iso.service.application.ServiceConfigurationService;
import com.temaconsulting.agente_iso.service.application.ServiceQueryService;
import com.temaconsulting.agente_iso.service.application.model.ServiceView;
import com.temaconsulting.agente_iso.service.application.model.TicketSummary;
import com.temaconsulting.agente_iso.shared.api.mapper.PageMapper;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.shared.api.dto.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {
    private final ServiceConfigurationService configuration;
    private final ServiceQueryService queries;

    public ServiceController(ServiceConfigurationService configuration, ServiceQueryService queries) {
        this.configuration = configuration;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public PageResponse<ServiceResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ServiceView> result = queries.list(page, size);
        return new PageResponse<>(
                result.items().stream().map(ServiceApiMapper::toResponse).toList(),
                PageMapper.toPageInfo(result.page(), result.size(), result.totalElements())
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceResponse create(@RequestBody(required = false) ServiceWriteRequest request) {
        return ServiceApiMapper.toResponse(configuration.create(request == null ? null : request.toCommand()));
    }

    @GetMapping("/{serviceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public ServiceResponse get(@PathVariable UUID serviceId) {
        return ServiceApiMapper.toResponse(queries.get(serviceId));
    }

    @PutMapping("/{serviceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceResponse update(
            @PathVariable UUID serviceId,
            @RequestBody(required = false) ServiceWriteRequest request
    ) {
        return ServiceApiMapper.toResponse(configuration.update(serviceId, request == null ? null : request.toCommand()));
    }

    @GetMapping("/{serviceId}/verifiable-processes")
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public List<VerifiableProcessResponse> verifiableProcesses(@PathVariable UUID serviceId) {
        return queries.verifiableProcesses(serviceId).stream()
                .map(ServiceApiMapper::toVerifiableProcess).toList();
    }

    @GetMapping("/{serviceId}/tickets")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<TicketSummary> tickets(
            @PathVariable UUID serviceId,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<TicketSummary> result = queries.tickets(serviceId, query, page, size);
        return new PageResponse<>(result.items(),
                PageMapper.toPageInfo(result.page(), result.size(), result.totalElements()));
    }
}
