package com.temaconsulting.agente_iso.verification.application;

import org.springframework.stereotype.Service;

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

    public record RunWithProgress(VerificationRunSnapshot run, VerificationResultProgress progress) {}
}
