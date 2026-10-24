package com.temaconsulting.agente_iso.verification.application.model;

import com.temaconsulting.agente_iso.verification.domain.ResultStatus;

import java.util.List;
import java.util.Objects;

/** Effective result of a single recheck; the original automatic proposal remains immutable. */
public record VerificationResultRecheck(
        ResultStatus status,
        String rationale,
        List<String> limitations,
        List<VerificationResultWriteCommand.EvidenceReference> evidence
) {
    public VerificationResultRecheck {
        Objects.requireNonNull(status);
        Objects.requireNonNull(rationale);
        limitations = List.copyOf(limitations);
        evidence = List.copyOf(evidence);
    }
}
