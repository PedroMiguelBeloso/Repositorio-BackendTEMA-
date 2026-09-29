package com.temaconsulting.agente_iso.identity.application;

import com.temaconsulting.agente_iso.identity.domain.Role;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.domain.UserRepository;
import com.temaconsulting.agente_iso.shared.dto.Page;
import com.temaconsulting.agente_iso.shared.dto.PageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock Clock clock;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, clock);
    }

    @Test
    void listDelegatesPagination() {
        Page<User> expected = new Page<>(
                List.of(),
                new PageInfo(0, 20, 0, 0)
        );
        when(userRepository.list(0, 20)).thenReturn(expected);

        assertEquals(expected, userService.list(0, 20));
    }

    @Test
    void createReaderAlwaysCreatesReaderWithHashedPassword() {
        Instant now = Instant.parse("2026-09-24T20:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(passwordEncoder.encode("initial-password")).thenReturn("hashed-password");
        when(userRepository.findByEmail(" READER@TEMA.TEST ")).thenReturn(Optional.empty());
        when(userRepository.insert(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createReader(
                " READER@TEMA.TEST ",
                " Reader ",
                "initial-password"
        );

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        User stored = captor.getValue();

        assertEquals(Role.READER, stored.role());
        assertEquals("reader@tema.test", stored.email());
        assertEquals("Reader", stored.displayName());
        assertEquals("hashed-password", stored.passwordHash());
        assertEquals(now, stored.createdAt());
        assertEquals(stored, result);
    }

    @Test
    void createReaderRejectsDuplicateEmailBeforeEncodingPassword() {
        User existing = User.createReader(
                UUID.randomUUID(),
                "reader@tema.test",
                "Existing",
                "hash",
                Instant.parse("2026-09-24T20:00:00Z")
        );
        when(userRepository.findByEmail("reader@tema.test")).thenReturn(Optional.of(existing));

        assertThrows(
                DuplicateUserEmailException.class,
                () -> userService.createReader("reader@tema.test", "Reader", "initial-password")
        );

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).insert(any());
    }
}
