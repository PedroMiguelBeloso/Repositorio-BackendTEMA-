package com.temaconsulting.agente_iso.process.api;

import com.temaconsulting.agente_iso.process.api.dto.CreateProcessRequest;
import com.temaconsulting.agente_iso.process.api.dto.ProcessDefinitionResponse;
import com.temaconsulting.agente_iso.process.api.mapper.ProcessApiMapper;
import com.temaconsulting.agente_iso.process.application.ProcessService;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.shared.api.PageMapper;
import com.temaconsulting.agente_iso.shared.applicacion.Page;
import com.temaconsulting.agente_iso.shared.dto.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/processes")
public class ProcessController {

    private final ProcessService processes;

    public ProcessController(ProcessService processes) {
        this.processes = processes;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public PageResponse<ProcessDefinitionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProcessDefinition> result = processes.list(page, size);

        return new PageResponse<>(
                result.items().stream()
                        .map(ProcessApiMapper::toResponse)
                        .toList(),
                PageMapper.toPageInfo(result.page(), result.size(), result.totalElements())
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProcessDefinitionResponse create(@RequestBody CreateProcessRequest request) {
        return ProcessApiMapper.toResponse(
                processes.create(
                        request == null ? null : request.code(),
                        request == null ? null : request.name()
                )
        );
    }

    @GetMapping("/{processId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public ProcessDefinitionResponse get(@PathVariable UUID processId) {
        return ProcessApiMapper.toResponse(processes.get(processId));
    }
}
