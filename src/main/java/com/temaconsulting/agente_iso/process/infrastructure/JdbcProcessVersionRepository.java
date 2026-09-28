package com.temaconsulting.agente_iso.process.infrastructure;

import com.temaconsulting.agente_iso.process.application.ProcessVersionRepository;
import com.temaconsulting.agente_iso.process.domain.*;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.Page;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public class JdbcProcessVersionRepository implements ProcessVersionRepository {

    private final JdbcTemplate jdbc;

    public JdbcProcessVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ProcessVersion replaceDraft(ProcessVersion version, int expectedRevision) {
        // Bloqueo optimista raíz
        int updatedRows = jdbc.update(
                "UPDATE process_versions SET objective = ?, owner = ?, version_date = ?, official_version = ?, revision = revision + 1 "
                        +
                        "WHERE id = ? AND revision = ? AND status = 'DRAFT'::process_status",
                version.objective(), version.owner(), version.versionDate(), version.officialVersion(), version.id(),
                expectedRevision);
        if (updatedRows == 0) {
            throw new ApiException(ProblemCode.STALE_REVISION, HttpStatus.CONFLICT,
                    "La versión fue modificada por alguien más.", List.of());
        }

        syncPolicies(version);
        syncMacroActivities(version);
        syncActivities(version);
        syncExpectations(version);

        return findWithTree(version.id()).orElseThrow();
    }

    private void syncPolicies(ProcessVersion version) {
        // 1. Eliminar las políticas que ya no vienen en el request para esta versión
        List<UUID> incoming = version.policies().stream().map(ProcessPolicy::id).toList();
        deleteOmitted("process_policies", "process_version_id", version.id(), incoming);

        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();

        for (ProcessPolicy p : version.policies()) {
            // Verificamos si la política ya existe específicamente para esta versión
            Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM process_policies WHERE id = ? AND process_version_id = ?",
                Integer.class,
                p.id(), version.id()
            );

            if (count != null && count > 0) {
                updates.add(new Object[] { p.order(), p.text(), p.id() });
            } else {
                inserts.add(new Object[] { p.id(), version.id(), p.order(), p.text() });
            }
        }

        if (!inserts.isEmpty()) {
            jdbc.batchUpdate(
                "INSERT INTO process_policies (id, process_version_id, \"order\", text) VALUES (?, ?, ?, ?)",
                inserts
            );
        }
        if (!updates.isEmpty()) {
            jdbc.batchUpdate(
                "UPDATE process_policies SET \"order\" = ?, text = ? WHERE id = ?",
                updates
            );
        }
    }

    private void syncMacroActivities(ProcessVersion version) {
        List<UUID> incoming = version.macroActivities().stream().map(MacroActivity::id).toList();
        deleteOmitted("macro_activities", "process_version_id", version.id(), incoming);

        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        for (MacroActivity m : version.macroActivities()) {
            if (exists("macro_activities", m.id())) {
                updates.add(new Object[] { m.name(), m.description(), m.order(), m.id() });
            } else {
                inserts.add(new Object[] { m.id(), version.id(), m.name(), m.description(), m.order() });
            }
        }
        if (!inserts.isEmpty())
            jdbc.batchUpdate(
                    "INSERT INTO macro_activities (id, process_version_id, name, description, \"order\") VALUES (?, ?, ?, ?, ?)",
                    inserts);
        if (!updates.isEmpty())
            jdbc.batchUpdate("UPDATE macro_activities SET name = ?, description = ?, \"order\" = ? WHERE id = ?",
                    updates);
    }

    private void syncActivities(ProcessVersion version) {
        List<UUID> incoming = version.macroActivities().stream().flatMap(m -> m.activities().stream()).map(Activity::id)
                .toList();

        if (incoming.isEmpty()) {
            jdbc.update(
                    "DELETE FROM activities WHERE macro_activity_id IN (SELECT id FROM macro_activities WHERE process_version_id = ?)",
                    version.id());
        } else {
            jdbc.update(
                    "DELETE FROM activities WHERE macro_activity_id IN (SELECT id FROM macro_activities WHERE process_version_id = ?) AND id != ALL(?)",
                    version.id(), incoming.toArray(new UUID[0]));
        }

        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        for (MacroActivity m : version.macroActivities()) {
            for (Activity a : m.activities()) {
                if (exists("activities", a.id())) {
                    updates.add(new Object[] { a.name(), a.responsible(), a.instructions(), a.etaText(), a.order(),
                            a.id() });
                } else {
                    inserts.add(new Object[] { a.id(), m.id(), a.name(), a.responsible(), a.instructions(), a.etaText(),
                            a.order() });
                }
            }
        }
        if (!inserts.isEmpty())
            jdbc.batchUpdate(
                    "INSERT INTO activities (id, macro_activity_id, name, responsible, instructions, eta_text, \"order\") VALUES (?, ?, ?, ?, ?, ?, ?)",
                    inserts);
        if (!updates.isEmpty())
            jdbc.batchUpdate(
                    "UPDATE activities SET name = ?, responsible = ?, instructions = ?, eta_text = ?, \"order\" = ? WHERE id = ?",
                    updates);
    }

    private void syncExpectations(ProcessVersion version) {
        List<UUID> incoming = version.macroActivities().stream()
                .flatMap(m -> Stream.concat(m.inputs().stream(),
                        Stream.concat(m.outputs().stream(),
                                m.activities().stream().flatMap(a -> a.records().stream()))))
                .map(e -> e.id()).toList();

        if (incoming.isEmpty()) {
            jdbc.update(
                    "DELETE FROM evidence_expectations WHERE macro_activity_id IN (SELECT id FROM macro_activities WHERE process_version_id = ?)",
                    version.id());
        } else {
            jdbc.update(
                    "DELETE FROM evidence_expectations WHERE macro_activity_id IN (SELECT id FROM macro_activities WHERE process_version_id = ?) AND id != ALL(?)",
                    version.id(), incoming.toArray(new UUID[0]));
        }

        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        for (MacroActivity m : version.macroActivities()) {
            List<EvidenceExpectation> allExp = Stream.concat(m.inputs().stream(),
                    Stream.concat(m.outputs().stream(), m.activities().stream().flatMap(a -> a.records().stream())))
                    .toList();
            for (EvidenceExpectation e : allExp) {
                if (exists("evidence_expectations", e.id())) {
                    updates.add(new Object[] { e.displayName(), e.applicability().name(), e.cardinality().name(),
                            e.order(), e.id() });
                } else {
                    inserts.add(
                            new Object[] { e.id(), m.id(), e.activityId(), e.registryDefinitionId(), e.displayName(),
                                    e.applicability().name(), e.cardinality().name(), e.location().name(), e.order() });
                }
            }
        }
        if (!inserts.isEmpty())
            jdbc.batchUpdate(
                    "INSERT INTO evidence_expectations (id, macro_activity_id, activity_id, registry_definition_id, display_name, applicability, cardinality, location, \"order\") VALUES (?, ?, ?, ?, ?, ?::applicability_mode, ?::cardinality_enum, ?::evidence_location, ?)",
                    inserts);
        if (!updates.isEmpty())
            jdbc.batchUpdate(
                    "UPDATE evidence_expectations SET display_name = ?, applicability = ?::applicability_mode, cardinality = ?::cardinality_enum, \"order\" = ? WHERE id = ?",
                    updates);
    }

    private void deleteOmitted(String table, String parentColumn, UUID parentId, List<UUID> incomingIds) {
        if (incomingIds.isEmpty()) {
            jdbc.update("DELETE FROM " + table + " WHERE " + parentColumn + " = ?", parentId);
        } else {
            jdbc.update("DELETE FROM " + table + " WHERE " + parentColumn + " = ? AND id != ALL(?)",
                    parentId, incomingIds.toArray(new UUID[0]));
        }
    }

    private boolean exists(String table, UUID id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean allRegistriesExist(List<UUID> ids) {
        if (ids.isEmpty())
            return true;
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM registry_definitions WHERE id = ALL(?)",
                Integer.class,
                new Object[] { ids.toArray(new UUID[0]) });
        return count != null && count == ids.size();
    }

    public Optional<ProcessVersion> findById(UUID id) {
        RowMapper<ProcessVersion> mapper = (rs, rowNum) -> new ProcessVersion(
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
                rs.getTimestamp("published_at") != null ? rs.getTimestamp("published_at").toInstant() : null,
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null);
        List<ProcessVersion> list = jdbc.query("SELECT * FROM process_versions WHERE id = ?", mapper, id);
        return list.stream().findFirst();
    }

    @Override
    public ProcessVersion insertDraft(ProcessVersion version) {
        jdbc.update(
                "INSERT INTO process_versions (id, process_id, official_version, version_date, status, revision, objective, owner, created_at) " +
                        "VALUES (?, ?, ?, ?, ?::process_status, ?, ?, ?, ?)",
                version.id(),
                version.processId(),
                version.officialVersion(),
                version.versionDate(),
                version.status().name(),
                version.revision(),
                version.objective(),
                version.owner(),
                java.sql.Timestamp.from(version.createdAt())
        );
        return version;
    }

    @Override
    public Optional<ProcessVersion> findWithTree(UUID id) {
        Optional<ProcessVersion> versionOpt = findById(id);
        if (versionOpt.isEmpty()) {
            return Optional.empty();
        }
        ProcessVersion v = versionOpt.get();

        // 1. Cargar políticas asociadas a la versión del proceso
        List<ProcessPolicy> policies = jdbc.query(
            "SELECT id, \"order\", text FROM process_policies WHERE process_version_id = ? ORDER BY \"order\", id",
            (rs, rowNum) -> new ProcessPolicy(
                rs.getObject("id", UUID.class),
                rs.getInt("order"),
                rs.getString("text")
            ),
            id
        );

        // 2. Cargar macroactividades
        List<MacroActivity> macroActivities = jdbc.query(
            "SELECT id, process_version_id, name, description, \"order\" FROM macro_activities WHERE process_version_id = ? ORDER BY \"order\", id",
            (rs, rowNum) -> {
                UUID macroId = rs.getObject("id", UUID.class);
                
                // Expectativas de tipo INPUT para la macroactividad
                List<EvidenceExpectation> inputs = loadExpectations(macroId, null, EvidenceLocation.INPUT);
                // Expectativas de tipo OUTPUT para la macroactividad
                List<EvidenceExpectation> outputs = loadExpectations(macroId, null, EvidenceLocation.OUTPUT);
                
                // Actividades hijas de esta macroactividad
                List<Activity> activities = jdbc.query(
                    "SELECT id, macro_activity_id, name, responsible, instructions, eta_text, \"order\" FROM activities WHERE macro_activity_id = ? ORDER BY \"order\", id",
                    (actRs, actRowNum) -> {
                        UUID actId = actRs.getObject("id", UUID.class);
                        List<EvidenceExpectation> records = loadExpectations(macroId, actId, EvidenceLocation.ACTIVITY_RECORD);
                        return new Activity(
                            actId,
                            macroId,
                            actRs.getString("name"),
                            actRs.getString("responsible"),
                            actRs.getString("instructions"),
                            actRs.getString("eta_text"),
                            actRs.getInt("order"),
                            records
                        );
                    },
                    macroId
                );

                return new MacroActivity(
                    macroId,
                    rs.getObject("process_version_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getInt("order"),
                    activities,
                    inputs,
                    outputs
                );
            },
            id
        );

        // Reconstruir la versión con el árbol completo hidratado
        ProcessVersion fullVersion = new ProcessVersion(
            v.id(),
            v.processId(),
            v.officialVersion(),
            v.versionDate(),
            v.status(),
            v.revision(),
            v.objective(),
            v.owner(),
            policies,
            macroActivities,
            v.publishedAt(),
            v.createdAt()
        );

        return Optional.of(fullVersion);
    }

    private List<EvidenceExpectation> loadExpectations(UUID macroId, UUID activityId, EvidenceLocation location) {
        String sql;
        Object[] params;
        if (activityId == null) {
            sql = "SELECT id, macro_activity_id, activity_id, registry_definition_id, display_name, applicability, cardinality, location, \"order\" FROM evidence_expectations WHERE macro_activity_id = ? AND activity_id IS NULL AND location = ?::evidence_location ORDER BY \"order\", id";
            params = new Object[]{macroId, location.name()};
        } else {
            sql = "SELECT id, macro_activity_id, activity_id, registry_definition_id, display_name, applicability, cardinality, location, \"order\" FROM evidence_expectations WHERE activity_id = ? AND location = ?::evidence_location ORDER BY \"order\", id";
            params = new Object[]{activityId, location.name()};
        }

        return jdbc.query(sql, (rs, rowNum) -> new EvidenceExpectation(
            rs.getObject("id", UUID.class),
            rs.getObject("macro_activity_id", UUID.class),
            rs.getObject("activity_id", UUID.class),
            rs.getObject("registry_definition_id", UUID.class),
            rs.getString("display_name"),
            ApplicabilityMode.valueOf(rs.getString("applicability")),
            Cardinality.valueOf(rs.getString("cardinality")),
            EvidenceLocation.valueOf(rs.getString("location")),
            rs.getInt("order")
        ), params);
    }

    @Override
    public Optional<ProcessVersion> findByOfficialVersion(UUID processId, String officialVersion) {
        List<ProcessVersion> list = jdbc.query(
            "SELECT * FROM process_versions WHERE process_id = ? AND official_version = ?",
            (rs, rowNum) -> findById(rs.getObject("id", UUID.class)).orElse(null),
            processId, officialVersion
        );
        return list.stream().filter(Objects::nonNull).findFirst();
    }

    @Override
    public Page<ProcessVersion> listByProcess(UUID processId, int page, int size) {
        int offset = page * size;
        List<ProcessVersion> content = jdbc.query(
            "SELECT id FROM process_versions WHERE process_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (rs, rowNum) -> findById(rs.getObject("id", UUID.class)).orElse(null),
            processId, size, offset
        ).stream().filter(Objects::nonNull).toList();

        Integer total = jdbc.queryForObject(
            "SELECT count(*) FROM process_versions WHERE process_id = ?",
            Integer.class,
            processId
        );
        int totalElements = total != null ? total : 0;

        return new Page<>(content, page, size, totalElements);
    }
}
