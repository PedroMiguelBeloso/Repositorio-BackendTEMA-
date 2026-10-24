package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.port.VerificationResultPort;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultProgress;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultQueryFailure;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultValidationException;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;

import com.temaconsulting.agente_iso.shared.application.model.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VerificationRunQueryService {
    private final VerificationRunRepository runs;
    private final VerificationResultPort results;

    public VerificationRunQueryService(VerificationRunRepository runs, VerificationResultPort results) {
        this.runs = runs;
        this.results = results;
    }

    public RunWithProgress get(UUID id) {
        VerificationRunSnapshot run = runs.findById(id)
                .orElseThrow(() -> VerificationResultQueryFailure.notFound(id));
        return new RunWithProgress(run, results.progress(id));
    }

    public Page<RunWithProgress> list(UUID serviceId, int page, int size) {
        if (page < 0) {
            throw new VerificationResultValidationException("page", "page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new VerificationResultValidationException("size", "size must be between 1 and 100");
        }

        Page<VerificationRunSnapshot> result = runs.list(serviceId, page, size);
        List<UUID> runIds = result.items().stream().map(VerificationRunSnapshot::id).toList();
        Map<UUID, VerificationResultProgress> progressByRun = results.progress(runIds);
        List<RunWithProgress> items = result.items().stream()
                .map(run -> new RunWithProgress(
                        run,
                        progressByRun.getOrDefault(run.id(), new VerificationResultProgress(0, 0))
                ))
                .toList();
        return new Page<>(items, result.page(), result.size(), result.totalElements());
    }

    public record RunWithProgress(VerificationRunSnapshot run, VerificationResultProgress progress) {}
}
