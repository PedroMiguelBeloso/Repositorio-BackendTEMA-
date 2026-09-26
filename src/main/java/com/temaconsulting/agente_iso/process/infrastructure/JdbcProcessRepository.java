package com.temaconsulting.agente_iso.process.infrastructure;

import com.temaconsulting.agente_iso.process.application.DuplicateProcessCodeException;
import com.temaconsulting.agente_iso.process.application.ProcessRepository;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.shared.application.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcProcessRepository implements ProcessRepository {

    private static final RowMapper<ProcessDefinition> ROW_MAPPER = (rs, rowNum) ->
            new ProcessDefinition(
                    rs.getObject("id", UUID.class),
                    ProcessCode.valueOf(rs.getString("code")),
                    rs.getString("name"),
                    rs.getBoolean("m2_enabled"),
                    rs.getTimestamp("created_at").toInstant()
            );

    private final JdbcTemplate jdbc;

    public JdbcProcessRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProcessDefinition> findById(UUID id) {
        return jdbc.query(
                        """
                        SELECT id, code, name, m2_enabled, created_at
                        FROM processes
                        WHERE id = ?
                        """,
                        ROW_MAPPER,
                        id
                )
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProcessDefinition> findByCode(ProcessCode code) {
        return jdbc.query(
                        """
                        SELECT id, code, name, m2_enabled, created_at
                        FROM processes
                        WHERE code = ?::process_code
                        """,
                        ROW_MAPPER,
                        code.name()
                )
                .stream()
                .findFirst();
    }

    @Override
    public Page<ProcessDefinition> list(int page, int size) {
        int offset = Math.multiplyExact(page, size);

        List<ProcessDefinition> items = jdbc.query(
                """
                SELECT id, code, name, m2_enabled, created_at
                FROM processes
                ORDER BY created_at ASC, id ASC
                LIMIT ? OFFSET ?
                """,
                ROW_MAPPER,
                size,
                offset
        );

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM processes",
                Long.class
        );

        return new Page<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public ProcessDefinition insert(ProcessDefinition process) {
        try {
            jdbc.update(
                    """
                    INSERT INTO processes (id, code, name, m2_enabled, created_at)
                    VALUES (?, ?::process_code, ?, ?, ?)
                    """,
                    process.id(),
                    process.code().name(),
                    process.name(),
                    process.m2Enabled(),
                    Timestamp.from(process.createdAt())
            );
            return process;
        } catch (DuplicateKeyException ex) {
            throw new DuplicateProcessCodeException(process.code());
        }
    }
}
