package com.temaconsulting.agente_iso.process.domain;
import java.util.UUID;
public record EvidenceExpectation(
    UUID id, UUID macroActivityId, UUID activityId, UUID registryDefinitionId, 
    String displayName, ApplicabilityMode applicability, Cardinality cardinality, 
    EvidenceLocation location, int order
) {}