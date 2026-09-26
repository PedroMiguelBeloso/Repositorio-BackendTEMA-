package com.temaconsulting.agente_iso.identity.application;

import com.temaconsulting.agente_iso.identity.domain.Role;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.domain.UserRepository;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import com.temaconsulting.agente_iso.shared.dto.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
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
        validatePage(page, size);
        return userRepository.list(page, size);
    }

    public User createReader(String emailInput, String nameInput, String password) {
        String email = normalizeEmail(emailInput);
        String displayName = nameInput == null ? "" : nameInput.trim();

        if (displayName.isEmpty()) {
            throw new ApiException(
                    ProblemCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "displayName es obligatorio.",
                    java.util.List.of()
            );
        }

        if (password == null || password.length() < 8) {
            throw new ApiException(
                    ProblemCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "initialPassword debe tener al menos 8 caracteres.",
                    java.util.List.of()
            );
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ApiException(
                    ProblemCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Ya existe un usuario con ese email.",
                    java.util.List.of()
            );
        }

        User reader = new User(
                UUID.randomUUID(),
                email,
                displayName,
                passwordEncoder.encode(password),
                Role.READER,
                true,
                clock.instant()
        );

        try {
            return userRepository.insert(reader);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(
                    ProblemCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Ya existe un usuario con ese email.",
                    java.util.List.of()
            );
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new ApiException(
                    ProblemCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "page debe ser mayor o igual a 0.",
                    java.util.List.of()
            );
        }

        if (size < 1 || size > 100) {
            throw new ApiException(
                    ProblemCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "size debe estar entre 1 y 100.",
                    java.util.List.of()
            );
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
