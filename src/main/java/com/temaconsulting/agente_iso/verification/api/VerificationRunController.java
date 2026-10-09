package com.temaconsulting.agente_iso.verification.api;

import com.temaconsulting.agente_iso.verification.api.dto.StartVerificationRequest;
import com.temaconsulting.agente_iso.verification.api.dto.VerificationRunResponse;
import com.temaconsulting.agente_iso.verification.application.CreatedVerificationRun;
import com.temaconsulting.agente_iso.verification.application.VerificationStartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationRunController {
    private final VerificationStartService starts;

    public VerificationRunController(VerificationStartService starts) {
        this.starts = starts;
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
                new VerificationRunResponse.RunProgress(0, 0, 0),
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
}
