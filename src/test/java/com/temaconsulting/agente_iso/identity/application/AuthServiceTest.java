package com.temaconsulting.agente_iso.identity.application;

import com.temaconsulting.agente_iso.identity.api.dto.ChangePasswordRequest;
import com.temaconsulting.agente_iso.identity.api.dto.LoginRequest;
import com.temaconsulting.agente_iso.identity.api.dto.TokenResponse;
import com.temaconsulting.agente_iso.identity.domain.Role;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenIssuer jwtTokenIssuer;
    @Mock Clock clock;

    private AuthService authService;
    private User activeAdmin;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenIssuer,
                clock
        );
        activeAdmin = new User(
                UUID.randomUUID(),
                "admin@tema.test",
                "Admin",
                "$2a$10$hash",
                Role.ADMIN,
                true,
                Instant.parse("2026-09-24T20:00:00Z")
        );
    }

    @Test
    void validCredentialsIssueToken() {
        LoginRequest request = new LoginRequest(" ADMIN@TEMA.TEST ", "correct-password");
        TokenResponse expected = new TokenResponse(
                "jwt",
                "Bearer",
                Instant.parse("2026-09-24T22:00:00Z"),
                new com.temaconsulting.agente_iso.identity.api.dto.UserResponse(
                        activeAdmin.id(),
                        activeAdmin.email(),
                        activeAdmin.displayName(),
                        activeAdmin.role(),
                        true,
                        activeAdmin.createdAt()
                )
        );

        when(userRepository.findByEmail("admin@tema.test")).thenReturn(Optional.of(activeAdmin));
        when(passwordEncoder.matches("correct-password", activeAdmin.passwordHash())).thenReturn(true);
        when(jwtTokenIssuer.issue(activeAdmin)).thenReturn(expected);

        TokenResponse actual = authService.login(request);

        assertEquals(expected, actual);
        verify(jwtTokenIssuer).issue(activeAdmin);
    }

    @Test
    void nonexistentUserReturnsInvalidCredentials() {
        when(userRepository.findByEmail("missing@tema.test")).thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("missing@tema.test", "wrong"))
        );

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenIssuer, never()).issue(any());
    }

    @Test
    void inactiveUserReturnsInvalidCredentials() {
        User inactive = new User(
                activeAdmin.id(),
                activeAdmin.email(),
                activeAdmin.displayName(),
                activeAdmin.passwordHash(),
                activeAdmin.role(),
                false,
                activeAdmin.createdAt()
        );
        when(userRepository.findByEmail("admin@tema.test")).thenReturn(Optional.of(inactive));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("admin@tema.test", "correct-password"))
        );

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenIssuer, never()).issue(any());
    }

    @Test
    void wrongPasswordReturnsInvalidCredentials() {
        when(userRepository.findByEmail("admin@tema.test")).thenReturn(Optional.of(activeAdmin));
        when(passwordEncoder.matches("wrong", activeAdmin.passwordHash())).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("admin@tema.test", "wrong"))
        );

        verify(jwtTokenIssuer, never()).issue(any());
    }

    @Test
    void currentUserReturnsAuthenticatedActiveUser() {
        when(userRepository.findById(activeAdmin.id())).thenReturn(Optional.of(activeAdmin));

        assertEquals(activeAdmin, authService.currentUser(activeAdmin.id()));
    }

    @Test
    void changePasswordVerifiesCurrentPasswordAndStoresNewHash() {
        when(userRepository.findById(activeAdmin.id())).thenReturn(Optional.of(activeAdmin));
        when(passwordEncoder.matches("old-password", activeAdmin.passwordHash())).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        authService.changePassword(
                activeAdmin.id(),
                "old-password",
                "new-password"
        );

        verify(passwordEncoder).matches("old-password", activeAdmin.passwordHash());
        verify(passwordEncoder).encode("new-password");
        verify(userRepository).updatePassword(activeAdmin.id(), "new-hash");
    }
}
