package com.temaconsulting.agente_iso.identity.application;

import com.temaconsulting.agente_iso.identity.application.error.DuplicateUserEmailException;

import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.domain.UserRepository;
import com.temaconsulting.agente_iso.shared.api.dto.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public Page<User> list(int page, int size) {
        return userRepository.list(page, size);
    }

    @Transactional
    public User createReader(String email, String displayName, String initialPassword) {
        // The API DTO validates request shape. The service still uses the
        // domain factory so the Reader role and entity invariants cannot be
        // bypassed by another application caller.
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateUserEmailException();
        }

        User reader = User.createReader(
                UUID.randomUUID(),
                email,
                displayName,
                passwordEncoder.encode(initialPassword),
                clock.instant()
        );

        return userRepository.insert(reader);
    }
}
