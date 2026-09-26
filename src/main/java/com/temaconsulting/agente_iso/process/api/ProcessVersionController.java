package com.temaconsulting.agente_iso.process.api;

import com.temaconsulting.agente_iso.process.api.dto.CreateVersionRequest;
import com.temaconsulting.agente_iso.process.api.dto.ProcessVersionResponse;
import com.temaconsulting.agente_iso.process.api.mapper.ProcessApiMapper;
import com.temaconsulting.agente_iso.process.application.ProcessDraftService;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
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
@RequestMapping("/api/v1/processes/{processId}/versions")
public class ProcessVersionController {

    private final ProcessDraftService drafts;

    public ProcessVersionController(ProcessDraftService drafts) {
        this.drafts = drafts;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public PageResponse<ProcessVersionResponse> listVersions(
            @PathVariable UUID processId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProcessVersion> result = drafts.listVersions(processId, page, size);

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
    public ProcessVersionResponse createVersion(
            @PathVariable UUID processId,
            @RequestBody CreateVersionRequest request
    ) {
        return ProcessApiMapper.toResponse(drafts.createDraft(processId, request));
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public ProcessVersionResponse getVersion(
            @PathVariable UUID processId,
            @PathVariable UUID versionId
    ) {
        return ProcessApiMapper.toResponse(drafts.getVersion(processId, versionId));
    }
}
