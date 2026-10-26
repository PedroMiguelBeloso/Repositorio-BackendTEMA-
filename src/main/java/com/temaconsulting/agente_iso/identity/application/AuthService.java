package com.temaconsulting.agente_iso.identity.application;

import com.temaconsulting.agente_iso.identity.application.error.CurrentUserNotFoundException;
import com.temaconsulting.agente_iso.identity.application.error.InvalidCredentialsException;
import com.temaconsulting.agente_iso.identity.application.port.JwtTokenIssuer;

import com.temaconsulting.agente_iso.identity.api.dto.LoginRequest;
import com.temaconsulting.agente_iso.identity.api.dto.TokenResponse;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer jwtTokenIssuer;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenIssuer jwtTokenIssuer
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenIssuer = jwtTokenIssuer;
    }

    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmail(email)
                .filter(User::canLogin)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        log.info("login_succeeded userId={} role={}", user.id(), user.role());
        return jwtTokenIssuer.issue(user);
    }

    public User currentUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(User::canLogin)
                .orElseThrow(() -> new CurrentUserNotFoundException(userId));
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .filter(User::canLogin)
                .orElseThrow(() -> new CurrentUserNotFoundException(userId));

        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        String newHash = passwordEncoder.encode(newPassword);
        user.changePasswordHash(newHash);
        userRepository.updatePassword(user);
        log.info("password_changed userId={}", userId);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
