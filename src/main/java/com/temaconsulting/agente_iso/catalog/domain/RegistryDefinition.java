package com.temaconsulting.agente_iso.catalog.domain;
import java.util.UUID;

public record RegistryDefinition(
    UUID id, String key, String canonicalName, 
    EvidenceType evidenceType, String officialNomenclature, boolean preloaded
) {
    public boolean requiresNomenclature() {
        return evidenceType == EvidenceType.DOCUMENT || evidenceType == EvidenceType.FOLDER;
    }
}