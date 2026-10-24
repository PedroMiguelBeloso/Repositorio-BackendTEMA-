package com.temaconsulting.agente_iso.identity.domain;

import com.temaconsulting.agente_iso.shared.api.dto.Page;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID id);

    Page<User> list(int page, int size);

    boolean existsAdmin();

    User insert(User user);

    void updatePassword(User user);
}
