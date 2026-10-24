package com.temaconsulting.agente_iso.verification.api;

import com.temaconsulting.agente_iso.verification.api.dto.StartVerificationRequest;
import com.temaconsulting.agente_iso.verification.api.dto.VerificationRunResponse;
import com.temaconsulting.agente_iso.verification.application.model.CreatedVerificationRun;
import com.temaconsulting.agente_iso.verification.application.VerificationRunDispatchService;
import com.temaconsulting.agente_iso.verification.application.VerificationRunQueryService;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;
import com.temaconsulting.agente_iso.verification.application.VerificationStartService;
import com.temaconsulting.agente_iso.shared.api.mapper.PageMapper;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.shared.api.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationRunController {
    private final VerificationStartService starts;
    private final VerificationRunDispatchService dispatcher;
    private final VerificationRunQueryService queries;

    public VerificationRunController(VerificationStartService starts,
                                     VerificationRunDispatchService dispatcher,
                                     VerificationRunQueryService queries) {
        this.starts = starts;
        this.dispatcher = dispatcher;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public PageResponse<VerificationRunResponse> list(
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<VerificationRunQueryService.RunWithProgress> result = queries.list(serviceId, page, size);
        return new PageResponse<>(
                result.items().stream().map(this::toResponse).toList(),
                PageMapper.toPageInfo(result.page(), result.size(), result.totalElements())
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VerificationRunResponse> start(
            @Valid @RequestBody StartVerificationRequest request,
            Authentication authentication
    ) {
        UUID startedBy = UUID.fromString(authentication.getName());
        CreatedVerificationRun run = starts.start(
                request.toTarget(), request.serviceRevision(), startedBy, request.toSelectionInputs()
        );
        dispatcher.dispatch(run.id());
        List<VerificationRunResponse.TicketSelectionResponse> selections =
                run.ticketSelections().groups().stream()
                        .map(selection -> new VerificationRunResponse.TicketSelectionResponse(
                                selection.groupId(), selection.ticketIds()
                        ))
                        .toList();
        VerificationRunResponse response = new VerificationRunResponse(
                run.id(),
                run.serviceId(),
                run.serviceTypeSnapshot(),
                run.serviceRevisionSnapshot(),
                run.processVersionId(),
                new VerificationRunResponse.ServiceSourceSnapshot(
                        run.driveFolderId(),
                        run.driveFolderName(),
                        run.redmineProjectId(),
                        run.redmineProjectName()
                ),
                run.scope(),
                run.macroActivityId(),
                run.note(),
                run.status(),
                new VerificationRunResponse.RunProgress(run.total(), 0, 0),
                run.startedBy(),
                run.startedAt(),
                null,
                null,
                selections
        );
        String location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{runId}")
                .buildAndExpand(run.id())
                .toUriString();
        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, location)
                .body(response);
    }

    @GetMapping("/{runId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public VerificationRunResponse get(@PathVariable UUID runId) {
        return toResponse(queries.get(runId));
    }

    private VerificationRunResponse toResponse(VerificationRunQueryService.RunWithProgress state) {
        VerificationRunSnapshot run = state.run();
        List<VerificationRunResponse.TicketSelectionResponse> selections = run.ticketSelections().groups().stream()
                .map(selection -> new VerificationRunResponse.TicketSelectionResponse(
                        selection.groupId(), selection.ticketIds())).toList();
        return new VerificationRunResponse(
                run.id(), run.serviceId(), run.serviceTypeSnapshot(), run.serviceRevisionSnapshot(),
                run.processVersionId(),
                new VerificationRunResponse.ServiceSourceSnapshot(run.driveFolderId(), run.driveFolderName(),
                        run.redmineProjectId(), run.redmineProjectName()),
                run.scope(), run.macroActivityId(), run.note(), run.status(),
                new VerificationRunResponse.RunProgress(run.total(),
                        Math.toIntExact(state.progress().resultCount()),
                        Math.toIntExact(state.progress().reviewRequiredCount())),
                run.startedBy(), run.startedAt(), run.finishedAt(), run.failureMessage(), selections
        );
    }
}
