package com.temaconsulting.agente_iso.process.infrastructure;

import com.temaconsulting.agente_iso.process.application.ProcessVersionRepository;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.shared.application.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcProcessVersionRepository implements ProcessVersionRepository {

    private static final RowMapper<ProcessVersion> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp publishedAt = rs.getTimestamp("published_at");

        return new ProcessVersion(
                rs.getObject("id", UUID.class),
                rs.getObject("process_id", UUID.class),
                rs.getString("official_version"),
                rs.getObject("version_date", java.time.LocalDate.class),
                ProcessStatus.valueOf(rs.getString("status")),
                rs.getInt("revision"),
                rs.getString("objective"),
                rs.getString("owner"),
                publishedAt == null ? null : publishedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    };

    private final JdbcTemplate jdbc;

    public JdbcProcessVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProcessVersion> findWithTree(UUID id) {
        return jdbc.query(
                        """
                        SELECT id, process_id, official_version, objective, owner, version_date,
                               status, revision, published_at, created_at
                        FROM process_versions
                        WHERE id = ?
                        """,
                        ROW_MAPPER,
                        id
                )
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProcessVersion> findByOfficialVersion(UUID processId, String officialVersion) {
        return jdbc.query(
                        """
                        SELECT id, process_id, official_version, objective, owner, version_date,
                               status, revision, published_at, created_at
                        FROM process_versions
                        WHERE process_id = ? AND official_version = ?
                        """,
                        ROW_MAPPER,
                        processId,
                        officialVersion
                )
                .stream()
                .findFirst();
    }

    @Override
    public Page<ProcessVersion> listByProcess(UUID processId, int page, int size) {
        int offset = Math.multiplyExact(page, size);

        List<ProcessVersion> items = jdbc.query(
                """
                SELECT id, process_id, official_version, objective, owner, version_date,
                       status, revision, published_at, created_at
                FROM process_versions
                WHERE process_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                ROW_MAPPER,
                processId,
                size,
                offset
        );

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM process_versions WHERE process_id = ?",
                Long.class,
                processId
        );

        return new Page<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public ProcessVersion insertDraft(ProcessVersion version) {
        jdbc.update(
                """
                INSERT INTO process_versions (
                    id,
                    process_id,
                    official_version,
                    objective,
                    owner,
                    version_date,
                    status,
                    revision,
                    published_at,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?::process_status, ?, ?, ?)
                """,
                version.id(),
                version.processId(),
                version.officialVersion(),
                version.objective(),
                version.owner(),
                java.sql.Date.valueOf(version.versionDate()),
                version.status().name(),
                version.revision(),
                version.publishedAt() == null ? null : Timestamp.from(version.publishedAt()),
                Timestamp.from(version.createdAt())
        );

        return version;
    }
}
