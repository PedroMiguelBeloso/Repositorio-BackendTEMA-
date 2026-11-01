package com.temaconsulting.agente_iso.verification.api;

import com.temaconsulting.agente_iso.shared.api.mapper.PageMapper;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.shared.api.dto.PageResponse;
import com.temaconsulting.agente_iso.verification.api.dto.ResultStatusDecisionRequest;
import com.temaconsulting.agente_iso.verification.api.dto.AiRetryRequest;
import com.temaconsulting.agente_iso.verification.api.dto.AiRetryAttemptResponse;
import com.temaconsulting.agente_iso.verification.application.VerificationAiRetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.temaconsulting.agente_iso.verification.api.dto.ResolveApplicabilityRequest;
import com.temaconsulting.agente_iso.verification.application.VerificationApplicabilityService;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.api.dto.VerificationResultResponse;
import com.temaconsulting.agente_iso.verification.api.mapper.VerificationResultApiMapper;
import com.temaconsulting.agente_iso.verification.application.VerificationResultDecisionService;
import com.temaconsulting.agente_iso.verification.application.VerificationResultQueryService;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultRepository;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import org.springframework.http.HttpStatus;
import java.time.Instant;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationResultController {
    private final VerificationResultQueryService queries;
    private final VerificationResultDecisionService decisions;
    private final VerificationApplicabilityService applicability;
    private final VerificationResultApiMapper mapper;
    private final VerificationAiRetryService aiRetries;
    private final VerificationResultRepository resultRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public VerificationResultController(
            VerificationResultQueryService queries,
            VerificationResultDecisionService decisions,
            VerificationApplicabilityService applicability,
            VerificationResultApiMapper mapper,
            VerificationAiRetryService aiRetries,
            VerificationResultRepository resultRepository
    ) {
        this.queries = queries;
        this.decisions = decisions;
        this.applicability = applicability;
        this.mapper = mapper;
        this.aiRetries = aiRetries;
        this.resultRepository = resultRepository;
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

    @PostMapping("/{runId}/results/{resultId}/ai-retry")
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResultResponse retryAi(
            @PathVariable UUID runId, @PathVariable UUID resultId,
            @Valid @RequestBody AiRetryRequest request, Authentication authentication) {
        return mapper.toResponse(aiRetries.retry(runId, resultId, request.revision(),
                UUID.fromString(authentication.getName())));
    }

    @GetMapping("/{runId}/results/{resultId}/ai-retries")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AiRetryAttemptResponse> aiRetryHistory(@PathVariable UUID runId, @PathVariable UUID resultId) {
        return aiRetries.history(runId, resultId).stream().map(attempt -> {
            try {
                return new AiRetryAttemptResponse(attempt.id(), attempt.requestedBy(), attempt.attemptedAt(),
                        objectMapper.readValue(attempt.ticketSnapshotJson(), Object.class),
                        objectMapper.readValue(attempt.previousResultJson(), Object.class), attempt.previousRevision(),
                        attempt.previousProposedStatus(), attempt.previousEffectiveStatus(),
                        attempt.proposedStatus(), attempt.effectiveStatus(), attempt.applicabilityDecision(),
                        attempt.rationale(), attempt.outcome(), attempt.model(), attempt.responseId());
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Stored AI retry snapshot is invalid", ex);
            }
        }).toList();
    }

    @GetMapping("/{runId}/results/{resultId}/ticket-observation")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketObservationResponse ticketObservation(@PathVariable UUID runId, @PathVariable UUID resultId) {
        var observation = resultRepository.findTicketObservation(runId, resultId)
                .orElseThrow(() -> new ApiException(ProblemCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "No hay un ticket observado para este resultado.", List.of()));
        try {
            return new TicketObservationResponse(observation.observedAt(),
                    objectMapper.readValue(observation.snapshotJson(), Object.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Stored ticket observation is invalid", ex);
        }
    }

    public record TicketObservationResponse(Instant observedAt, Object ticket) { }
}
