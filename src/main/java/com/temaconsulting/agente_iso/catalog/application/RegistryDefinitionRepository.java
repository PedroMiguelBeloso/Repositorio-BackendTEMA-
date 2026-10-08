package com.temaconsulting.agente_iso.catalog.application;

import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.shared.application.Page;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RegistryDefinitionRepository {

    Optional<RegistryDefinition> findById(UUID id);

    List<RegistryDefinition> findAllByIds(Collection<UUID> ids);

    Optional<RegistryDefinition> findByKey(String key);

    Page<RegistryDefinition> list(int page, int size, String query);

    Page<RegistryDefinition> list(int page, int size, String query, EvidenceType evidenceType);

    RegistryDefinition insert(RegistryDefinition definition);
}
