package com.temaconsulting.agente_iso.catalog.application;

import com.temaconsulting.agente_iso.catalog.application.port.RegistryDefinitionRepository;

import com.temaconsulting.agente_iso.catalog.api.dto.CatalogDtos.CreateRegistryRequest;
import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RegistryCatalogService {

    private final RegistryDefinitionRepository repository;

    public RegistryCatalogService(RegistryDefinitionRepository repository) {
        this.repository = repository;
    }

    public Page<RegistryDefinition> list(int page, int size, String query, EvidenceType evidenceType) {
        return repository.list(page, size, query, evidenceType);
    }

    public RegistryDefinition create(CreateRegistryRequest request) {
        // Genera una key única basada en UUID para no colisionar
        String generatedKey = "REG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        RegistryDefinition def = new RegistryDefinition(
            UUID.randomUUID(), 
            generatedKey, 
            request.canonicalName(), 
            request.evidenceType(), 
            request.officialNomenclature(), 
            false
        );
        return repository.insert(def);
    }
}
