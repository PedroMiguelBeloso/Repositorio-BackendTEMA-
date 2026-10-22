package com.temaconsulting.agente_iso.identity.domain;

import com.temaconsulting.agente_iso.identity.domain.error.UserDomainValidationException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Domain entity for an application user.
 *
 * Creation is intentionally restricted to business factories so that the
 * application layer cannot accidentally create a Reader with another role.
 * Password changes are also performed through a business operation rather
 * than by exposing a writable password field.
 */
public final class User {

    private final UUID id;
    private final String email;
    private final String displayName;
    private String passwordHash;
    private final Role role;
    private final boolean active;
    private final Instant createdAt;

    private User(
            UUID id,
            String email,
            String displayName,
            String passwordHash,
            Role role,
            boolean active,
            Instant createdAt
    ) {
        this.id = requireId(id);
        this.email = normalizeEmail(email);
        this.displayName = requireDisplayName(displayName);
        this.passwordHash = requirePasswordHash(passwordHash);
        this.role = requireRole(role);
        this.active = active;
        this.createdAt = requireCreatedAt(createdAt);
    }

    /**
     * Business operation: register an active Reader.
     * The role and active state are part of the operation and cannot be
     * supplied by the caller.
     */
    public static User createReader(
            UUID id,
            String email,
            String displayName,
            String passwordHash,
            Instant createdAt
    ) {
        return new User(
                id,
                email,
                displayName,
                passwordHash,
                Role.READER,
                true,
                createdAt
        );
    }

    /**
     * Technical/bootstrap creation operation for the initial Administrator.
     * The normal user-management API never calls this factory.
     */
    public static User createAdmin(
            UUID id,
            String email,
            String displayName,
            String passwordHash,
            Instant createdAt
    ) {
        return new User(
                id,
                email,
                displayName,
                passwordHash,
                Role.ADMIN,
                true,
                createdAt
        );
    }

    /**
     * Reconstitutes a persisted user without applying creation-specific rules
     * such as forcing READER/active=true.
     */
    public static User reconstitute(
            UUID id,
            String email,
            String displayName,
            String passwordHash,
            Role role,
            boolean active,
            Instant createdAt
    ) {
        return new User(id, email, displayName, passwordHash, role, active, createdAt);
    }

    /** Business operation: replace this user's stored password hash. */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = requirePasswordHash(newPasswordHash);
    }

    public boolean canLogin() {
        return active;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static UUID requireId(UUID id) {
        if (id == null) {
            throw new UserDomainValidationException("El id del usuario es obligatorio.");
        }
        return id;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new UserDomainValidationException("El email del usuario es obligatorio.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new UserDomainValidationException("El display name del usuario es obligatorio.");
        }
        return displayName.trim();
    }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new UserDomainValidationException("El hash de la contraseña es obligatorio.");
        }
        return passwordHash;
    }

    private static Role requireRole(Role role) {
        if (role == null) {
            throw new UserDomainValidationException("El rol del usuario es obligatorio.");
        }
        return role;
    }

    private static Instant requireCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new UserDomainValidationException("La fecha de creación del usuario es obligatoria.");
        }
        return createdAt;
    }
}
