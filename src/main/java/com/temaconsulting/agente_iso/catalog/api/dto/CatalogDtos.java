package com.temaconsulting.agente_iso.catalog.api.dto;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CatalogDtos {
    public record CreateRegistryRequest(
        @NotBlank String canonicalName,
        @NotNull EvidenceType evidenceType,
        String officialNomenclature
    ) {}

    public record RegistryDefinitionResponse(
        UUID id,
        String key,
        String canonicalName,
        EvidenceType evidenceType,
        String officialNomenclature,
        boolean preloaded
    ) {}
}