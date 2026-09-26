package com.temaconsulting.agente_iso.process.infrastructure;

import com.temaconsulting.agente_iso.process.application.DuplicateOfficialVersionException;
import com.temaconsulting.agente_iso.process.application.ProcessVersionRepository;
import com.temaconsulting.agente_iso.process.application.StaleProcessVersionException;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessPolicy;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.shared.application.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcProcessVersionRepository implements ProcessVersionRepository {

    private static final RowMapper<ProcessVersion> BASE_ROW_MAPPER = (rs, rowNum) -> {
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
                List.of(),
                List.of(),
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
        List<ProcessVersion> base = jdbc.query(
                """
                SELECT id, process_id, official_version, objective, owner, version_date,
                       status, revision, published_at, created_at
                FROM process_versions
                WHERE id = ?
                """,
                BASE_ROW_MAPPER,
                id
        );

        if (base.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(loadTrees(base).getFirst());
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
                        BASE_ROW_MAPPER,
                        processId,
                        officialVersion
                )
                .stream()
                .findFirst();
    }

    @Override
    public Page<ProcessVersion> listByProcess(UUID processId, int page, int size) {
        int offset = Math.multiplyExact(page, size);

        List<ProcessVersion> baseItems = jdbc.query(
                """
                SELECT id, process_id, official_version, objective, owner, version_date,
                       status, revision, published_at, created_at
                FROM process_versions
                WHERE process_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                BASE_ROW_MAPPER,
                processId,
                size,
                offset
        );

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM process_versions WHERE process_id = ?",
                Long.class,
                processId
        );

        return new Page<>(
                loadTrees(baseItems),
                page,
                size,
                total == null ? 0 : total
        );
    }

    @Override
    public ProcessVersion insertDraft(ProcessVersion version) {
        try {
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
        } catch (DuplicateKeyException ex) {
            throw new DuplicateOfficialVersionException(version.officialVersion());
        }
    }

    @Override
    public ProcessVersion replaceDraft(ProcessVersion version, int expectedRevision) {
        try {
            int updated = jdbc.update(
                    """
                    UPDATE process_versions
                    SET official_version = ?,
                        version_date = ?,
                        objective = ?,
                        owner = ?,
                        revision = ?
                    WHERE id = ?
                      AND process_id = ?
                      AND status = 'DRAFT'::process_status
                      AND revision = ?
                    """,
                    version.officialVersion(),
                    java.sql.Date.valueOf(version.versionDate()),
                    version.objective(),
                    version.owner(),
                    version.revision(),
                    version.id(),
                    version.processId(),
                    expectedRevision
            );

            if (updated == 0) {
                throw new StaleProcessVersionException(version.id(), expectedRevision);
            }

            jdbc.update(
                    "DELETE FROM process_policies WHERE process_version_id = ?",
                    version.id()
            );
            jdbc.update(
                    "DELETE FROM macro_activities WHERE process_version_id = ?",
                    version.id()
            );

            insertPolicies(version);
            insertMacroActivities(version);

            return findWithTree(version.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "Process version disappeared after replacement: " + version.id()
                    ));
        } catch (DuplicateKeyException ex) {
            throw new DuplicateOfficialVersionException(version.officialVersion());
        }
    }

    private void insertPolicies(ProcessVersion version) {
        if (version.policies().isEmpty()) {
            return;
        }

        jdbc.batchUpdate(
                """
                INSERT INTO process_policies (id, process_version_id, "order", text)
                VALUES (?, ?, ?, ?)
                """,
                version.policies(),
                version.policies().size(),
                (ps, policy) -> {
                    ps.setObject(1, policy.id());
                    ps.setObject(2, version.id());
                    ps.setInt(3, policy.order());
                    ps.setString(4, policy.text());
                }
        );
    }

    private void insertMacroActivities(ProcessVersion version) {
        if (version.macroActivities().isEmpty()) {
            return;
        }

        jdbc.batchUpdate(
                """
                INSERT INTO macro_activities (id, process_version_id, name, description, "order")
                VALUES (?, ?, ?, ?, ?)
                """,
                version.macroActivities(),
                version.macroActivities().size(),
                (ps, macro) -> {
                    ps.setObject(1, macro.id());
                    ps.setObject(2, version.id());
                    ps.setString(3, macro.name());
                    ps.setString(4, macro.description());
                    ps.setInt(5, macro.order());
                }
        );

        List<Activity> activities = version.macroActivities().stream()
                .flatMap(macro -> macro.activities().stream())
                .toList();
        if (activities.isEmpty()) {
            return;
        }

        jdbc.batchUpdate(
                """
                INSERT INTO activities (
                    id,
                    macro_activity_id,
                    name,
                    responsible,
                    instructions,
                    eta_text,
                    "order"
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                activities,
                activities.size(),
                (ps, activity) -> {
                    ps.setObject(1, activity.id());
                    ps.setObject(2, activity.macroActivityId());
                    ps.setString(3, activity.name());
                    ps.setString(4, activity.responsible());
                    ps.setString(5, activity.instructions());
                    ps.setString(6, activity.etaText());
                    ps.setInt(7, activity.order());
                }
        );
    }

    private List<ProcessVersion> loadTrees(List<ProcessVersion> baseVersions) {
        if (baseVersions.isEmpty()) {
            return List.of();
        }

        List<UUID> versionIds = baseVersions.stream()
                .map(ProcessVersion::id)
                .toList();
        String versionPlaceholders = placeholders(versionIds.size());

        Map<UUID, List<ProcessPolicy>> policiesByVersion = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT process_version_id, id, "order", text
                FROM process_policies
                WHERE process_version_id IN (%s)
                ORDER BY process_version_id, "order", id
                """.formatted(versionPlaceholders),
                rs -> {
                    UUID versionId = rs.getObject("process_version_id", UUID.class);
                    policiesByVersion.computeIfAbsent(versionId, ignored -> new ArrayList<>())
                            .add(new ProcessPolicy(
                                    rs.getObject("id", UUID.class),
                                    rs.getInt("order"),
                                    rs.getString("text")
                            ));
                },
                versionIds.toArray()
        );

        List<MacroRow> macroRows = jdbc.query(
                """
                SELECT process_version_id, id, name, description, "order"
                FROM macro_activities
                WHERE process_version_id IN (%s)
                ORDER BY process_version_id, "order", id
                """.formatted(versionPlaceholders),
                (rs, rowNum) -> new MacroRow(
                        rs.getObject("process_version_id", UUID.class),
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("order")
                ),
                versionIds.toArray()
        );

        Map<UUID, List<Activity>> activitiesByMacro = loadActivities(macroRows);
        Map<UUID, List<MacroActivity>> macrosByVersion = new LinkedHashMap<>();
        for (MacroRow row : macroRows) {
            MacroActivity macro = new MacroActivity(
                    row.id(),
                    row.processVersionId(),
                    row.name(),
                    row.description(),
                    row.order(),
                    activitiesByMacro.getOrDefault(row.id(), List.of())
            );
            macrosByVersion.computeIfAbsent(row.processVersionId(), ignored -> new ArrayList<>())
                    .add(macro);
        }

        return baseVersions.stream()
                .map(base -> new ProcessVersion(
                        base.id(),
                        base.processId(),
                        base.officialVersion(),
                        base.versionDate(),
                        base.status(),
                        base.revision(),
                        base.objective(),
                        base.owner(),
                        policiesByVersion.getOrDefault(base.id(), List.of()),
                        macrosByVersion.getOrDefault(base.id(), List.of()),
                        base.publishedAt(),
                        base.createdAt()
                ))
                .toList();
    }

    private Map<UUID, List<Activity>> loadActivities(List<MacroRow> macroRows) {
        if (macroRows.isEmpty()) {
            return Map.of();
        }

        List<UUID> macroIds = macroRows.stream()
                .map(MacroRow::id)
                .toList();
        String macroPlaceholders = placeholders(macroIds.size());

        Map<UUID, List<Activity>> activitiesByMacro = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT id, macro_activity_id, name, responsible, instructions, eta_text, "order"
                FROM activities
                WHERE macro_activity_id IN (%s)
                ORDER BY macro_activity_id, "order", id
                """.formatted(macroPlaceholders),
                rs -> {
                    UUID macroId = rs.getObject("macro_activity_id", UUID.class);
                    activitiesByMacro.computeIfAbsent(macroId, ignored -> new ArrayList<>())
                            .add(new Activity(
                                    rs.getObject("id", UUID.class),
                                    macroId,
                                    rs.getString("name"),
                                    rs.getString("responsible"),
                                    rs.getString("instructions"),
                                    rs.getString("eta_text"),
                                    rs.getInt("order")
                            ));
                },
                macroIds.toArray()
        );
        return activitiesByMacro;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private record MacroRow(
            UUID processVersionId,
            UUID id,
            String name,
            String description,
            int order
    ) {
    }
}
