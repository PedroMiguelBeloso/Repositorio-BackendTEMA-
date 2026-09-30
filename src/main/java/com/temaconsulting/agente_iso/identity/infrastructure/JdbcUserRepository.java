package com.temaconsulting.agente_iso.identity.infrastructure;

import com.temaconsulting.agente_iso.identity.application.DuplicateUserEmailException;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.domain.UserRepository;
import com.temaconsulting.agente_iso.shared.dto.PageInfo;
import com.temaconsulting.agente_iso.shared.dto.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserRepository implements UserRepository {

    private static final String BASE_SELECT = """
            SELECT id, email, display_name, password_hash, role, active, created_at
            FROM users
            """;

    private final JdbcTemplate jdbcTemplate;
    private final UserRowMapper mapper;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate, UserRowMapper mapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jdbcTemplate.query(
                BASE_SELECT + "WHERE lower(email) = lower(?) LIMIT 1",
                mapper,
                email.trim().toLowerCase(Locale.ROOT)
        ).stream().findFirst();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbcTemplate.query(
                BASE_SELECT + "WHERE id = ? LIMIT 1",
                mapper,
                id
        ).stream().findFirst();
    }

    @Override
    public Page<User> list(int page, int size) {
        long offset = (long) page * size;

        var items = jdbcTemplate.query(
                BASE_SELECT + "ORDER BY email, id LIMIT ? OFFSET ?",
                mapper,
                size,
                offset
        );

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users",
                Long.class
        );

        long totalElements = total == null ? 0L : total;
        int totalPages = totalElements == 0
                ? 0
                : (int) ((totalElements + size - 1L) / size);

        return new Page<>(
                items,
                new PageInfo(page, size, totalElements, totalPages)
        );
    }

    @Override
    public boolean existsAdmin() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE role = CAST(? AS role_enum))",
                Boolean.class,
                "ADMIN"
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public User insert(User user) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO users (
                        id, email, display_name, password_hash, role, active, created_at
                    ) VALUES (?, ?, ?, ?, CAST(? AS role_enum), ?, ?)
                    """,
                    user.id(),
                    user.email(),
                    user.displayName(),
                    user.passwordHash(),
                    user.role().name(),
                    user.active(),
                    Timestamp.from(user.createdAt())
            );
        } catch (DuplicateKeyException ex) {
            throw new DuplicateUserEmailException();
        }

        return user;
    }

    @Override
    public void updatePassword(User user) {
        int updated = jdbcTemplate.update(
                "UPDATE users SET password_hash = ? WHERE id = ?",
                user.passwordHash(),
                user.id()
        );

        if (updated != 1) {
            throw new IllegalStateException("No se pudo actualizar la contraseña del usuario");
        }
    }
}
