package com.temaconsulting.agente_iso.catalog.api;

import com.temaconsulting.agente_iso.catalog.api.dto.CatalogDtos.*;
import com.temaconsulting.agente_iso.catalog.application.RegistryCatalogService;
import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.shared.api.mapper.PageMapper;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.shared.api.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/registry-definitions")
public class RegistryDefinitionController {

    private final RegistryCatalogService catalogService;

    public RegistryDefinitionController(RegistryCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public PageResponse<RegistryDefinitionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) EvidenceType evidenceType) {
        Page<RegistryDefinition> results = catalogService.list(page, size, query, evidenceType);
        var items = results.items().stream().map(this::toResponse).toList();
        return new PageResponse<>(items,
                PageMapper.toPageInfo(results.page(), results.size(), results.totalElements()));
    }

    @PostMapping
    public RegistryDefinitionResponse create(@Valid @RequestBody CreateRegistryRequest request) {
        RegistryDefinition created = catalogService.create(request);
        return toResponse(created);
    }

    private RegistryDefinitionResponse toResponse(RegistryDefinition d) {
        return new RegistryDefinitionResponse(d.id(), d.key(), d.canonicalName(), d.evidenceType(), d.officialNomenclature(), d.preloaded());
    }
}
