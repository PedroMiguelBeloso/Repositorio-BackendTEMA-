package com.temaconsulting.agente_iso.identity.domain;

import com.temaconsulting.agente_iso.identity.domain.error.UserDomainValidationException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserDomainTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T20:00:00Z");

    @Test
    void createReaderOwnsReaderRoleAndCanonicalizesEmail() {
        User user = User.createReader(
                USER_ID,
                " READER@TEMA.TEST ",
                " Reader ",
                "hashed-password",
                CREATED_AT
        );

        assertEquals(Role.READER, user.role());
        assertTrue(user.active());
        assertEquals("reader@tema.test", user.email());
        assertEquals("Reader", user.displayName());
        assertEquals("hashed-password", user.passwordHash());
    }

    @Test
    void createReaderRejectsInvalidDomainState() {
        assertThrows(
                UserDomainValidationException.class,
                () -> User.createReader(USER_ID, "reader@tema.test", "   ", "hash", CREATED_AT)
        );

        assertThrows(
                UserDomainValidationException.class,
                () -> User.createReader(USER_ID, "reader@tema.test", "Reader", "   ", CREATED_AT)
        );
    }

    @Test
    void changePasswordHashChangesOnlyTheHash() {
        User user = User.createReader(
                USER_ID,
                "reader@tema.test",
                "Reader",
                "old-hash",
                CREATED_AT
        );

        user.changePasswordHash("new-hash");

        assertEquals("new-hash", user.passwordHash());
        assertEquals(USER_ID, user.id());
        assertEquals("reader@tema.test", user.email());
        assertEquals("Reader", user.displayName());
        assertEquals(Role.READER, user.role());
        assertTrue(user.active());
        assertEquals(CREATED_AT, user.createdAt());
    }

    @Test
    void inactiveReconstitutedUserCannotLogin() {
        User user = User.reconstitute(
                USER_ID,
                "reader@tema.test",
                "Reader",
                "hash",
                Role.READER,
                false,
                CREATED_AT
        );

        assertFalse(user.canLogin());
    }
}
