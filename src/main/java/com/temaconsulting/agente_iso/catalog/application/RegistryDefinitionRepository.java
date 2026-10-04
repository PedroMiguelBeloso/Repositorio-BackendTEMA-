package com.temaconsulting.agente_iso.catalog.application;

import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.shared.application.Page;

import java.util.Optional;
import java.util.UUID;

public interface RegistryDefinitionRepository {

    Optional<RegistryDefinition> findById(UUID id);

    Optional<RegistryDefinition> findByKey(String key);

    Page<RegistryDefinition> list(int page, int size, String query);

    Page<RegistryDefinition> list(int page, int size, String query, EvidenceType evidenceType);

    RegistryDefinition insert(RegistryDefinition definition);
}
