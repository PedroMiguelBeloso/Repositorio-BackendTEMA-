package com.temaconsulting.agente_iso.service.application.port;

import com.temaconsulting.agente_iso.service.domain.Service;
import com.temaconsulting.agente_iso.shared.application.model.Page;

import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository {
    Page<Service> list(int page, int size);

    Optional<Service> findById(UUID id);

    void insert(Service service);

    boolean replace(Service service, int expectedRevision);
}
