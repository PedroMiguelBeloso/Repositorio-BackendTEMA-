package com.temaconsulting.agente_iso.identity.application;

import com.temaconsulting.agente_iso.identity.api.dto.LoginRequest;
import com.temaconsulting.agente_iso.identity.api.dto.TokenResponse;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenIssuer jwtTokenIssuer,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenIssuer = jwtTokenIssuer;
        this.clock = clock;
    }

    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmail(email)
                .filter(User::canLogin)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        return jwtTokenIssuer.issue(user);
    }

    public User currentUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(User::canLogin)
                .orElseThrow(() -> new CurrentUserNotFoundException(userId));
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .filter(User::canLogin)
                .orElseThrow(() -> new CurrentUserNotFoundException(userId));

        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        String newHash = passwordEncoder.encode(newPassword);
        userRepository.updatePassword(userId, newHash);
        clock.instant();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
