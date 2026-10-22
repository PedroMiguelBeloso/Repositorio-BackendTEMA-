package com.temaconsulting.agente_iso.verification.api;

import com.temaconsulting.agente_iso.shared.api.mapper.PageMapper;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.shared.api.dto.PageResponse;
import com.temaconsulting.agente_iso.verification.api.dto.ResultStatusDecisionRequest;
import com.temaconsulting.agente_iso.verification.api.dto.ResolveApplicabilityRequest;
import com.temaconsulting.agente_iso.verification.application.VerificationApplicabilityService;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.api.dto.VerificationResultResponse;
import com.temaconsulting.agente_iso.verification.api.mapper.VerificationResultApiMapper;
import com.temaconsulting.agente_iso.verification.application.VerificationResultDecisionService;
import com.temaconsulting.agente_iso.verification.application.VerificationResultQueryService;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationResultController {
    private final VerificationResultQueryService queries;
    private final VerificationResultDecisionService decisions;
    private final VerificationApplicabilityService applicability;
    private final VerificationResultApiMapper mapper;

    public VerificationResultController(
            VerificationResultQueryService queries,
            VerificationResultDecisionService decisions,
            VerificationApplicabilityService applicability,
            VerificationResultApiMapper mapper
    ) {
        this.queries = queries;
        this.decisions = decisions;
        this.applicability = applicability;
        this.mapper = mapper;
    }

    @GetMapping("/{runId}/results")
    @PreAuthorize("hasAnyRole('ADMIN', 'READER')")
    public PageResponse<VerificationResultResponse> list(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<VerificationResult> results = queries.findCompletedRunResults(runId, page, size);
        return new PageResponse<>(
                results.items().stream().map(mapper::toResponse).toList(),
                PageMapper.toPageInfo(results.page(), results.size(), results.totalElements())
        );
    }

    @PostMapping("/{runId}/results/{resultId}/applicability")
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResultResponse resolveApplicability(
            @PathVariable UUID runId,
            @PathVariable UUID resultId,
            @Valid @RequestBody ResolveApplicabilityRequest request,
            Authentication authentication
    ) {
        return mapper.toResponse(applicability.resolve(runId, resultId, request.revision(),
                ApplicabilityDecision.valueOf(request.decision().name()), request.rationale(),
                UUID.fromString(authentication.getName())));
    }

    @PutMapping("/{runId}/results/{resultId}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResultResponse decide(
            @PathVariable UUID runId,
            @PathVariable UUID resultId,
            @Valid @RequestBody ResultStatusDecisionRequest request,
            Authentication authentication
    ) {
        return mapper.toResponse(decisions.decide(
                runId,
                resultId,
                request.revision(),
                request.finalStatus(),
                request.rationale(),
                UUID.fromString(authentication.getName())
        ));
    }
}
