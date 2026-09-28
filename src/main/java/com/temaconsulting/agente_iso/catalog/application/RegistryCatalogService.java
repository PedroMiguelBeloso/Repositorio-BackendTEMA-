package com.temaconsulting.agente_iso.catalog.application;

import com.temaconsulting.agente_iso.catalog.api.dto.CatalogDtos.CreateRegistryRequest;
import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.catalog.infrastructure.JdbcRegistryDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RegistryCatalogService {

    private final JdbcRegistryDefinitionRepository repository;

    public RegistryCatalogService(JdbcRegistryDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<RegistryDefinition> list(int page, int size, String query, EvidenceType type) {
        return repository.list(page, size, query, type);
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