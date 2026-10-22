package com.temaconsulting.agente_iso.verification.infrastructure.persistence;

import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.application.model.CreatedVerificationRun;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunDraft;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;
import com.temaconsulting.agente_iso.verification.application.error.VerificationStartFailure;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelection;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcVerificationRunRepository implements VerificationRunRepository {
    private static final String RUN_SELECT = """
            SELECT id, service_id, service_type_snapshot::text AS service_type_snapshot,
                   service_revision_snapshot, process_version_id, drive_folder_id,
                   drive_folder_name, redmine_project_id, redmine_project_name,
                   scope::text AS scope, macro_activity_id, note, status::text AS status,
                   started_by, started_at, finished_at, failure_message, total
            FROM verification_runs
            """;

    private final JdbcTemplate jdbc;

    public JdbcVerificationRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public CreatedVerificationRun create(VerificationRunDraft run) {
        Integer currentRevision = jdbc.query(
                "SELECT revision FROM services WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getInt("revision"),
                run.serviceId()
        ).stream().findFirst().orElseThrow(() ->
                VerificationStartFailure.missing("Service not found: " + run.serviceId()));
        if (currentRevision != run.serviceRevision()) {
            throw VerificationStartFailure.conflict(
                    "serviceRevision",
                    "Service configuration changed after preview; request a new preview before starting"
            );
        }

        jdbc.update(
                """
                INSERT INTO verification_runs (
                    id, service_id, process_version_id, started_by,
                    service_type_snapshot, service_revision_snapshot, scope,
                    macro_activity_id, note, status, total
                ) VALUES (?, ?, ?, ?, NULL, ?, ?::verification_scope, ?, ?, 'QUEUED'::run_status, ?)
                """,
                run.id(), run.serviceId(), run.processVersionId(), run.startedBy(),
                run.serviceRevision(), run.scope().name(), run.macroActivityId(), run.note(), run.total()
        );
        for (ValidatedTicketSelection group : run.ticketSelections().groups()) {
            for (String ticketId : group.ticketIds()) {
                jdbc.update(
                        """
                        INSERT INTO verification_ticket_selections (run_id, group_id, ticket_id)
                        VALUES (?, ?, ?)
                        """,
                        run.id(), group.groupId(), ticketId
                );
            }
        }
        return jdbc.queryForObject(
                """
                SELECT id, service_id, service_type_snapshot::text AS service_type_snapshot,
                       service_revision_snapshot, process_version_id, drive_folder_id,
                       drive_folder_name, redmine_project_id, redmine_project_name,
                       scope::text AS scope, macro_activity_id, note, status::text AS status,
                       started_by, started_at, total
                FROM verification_runs WHERE id = ?
                """,
                (rs, rowNum) -> mapRun(rs, rowNum, run.ticketSelections()),
                run.id()
        );
    }

    private CreatedVerificationRun mapRun(
            ResultSet rs,
            int rowNum,
            ValidatedTicketSelections selections
    ) throws SQLException {
        Timestamp startedAt = rs.getTimestamp("started_at");
        return new CreatedVerificationRun(
                rs.getObject("id", UUID.class),
                rs.getObject("service_id", UUID.class),
                ServiceType.valueOf(rs.getString("service_type_snapshot")),
                rs.getInt("service_revision_snapshot"),
                rs.getObject("process_version_id", UUID.class),
                rs.getString("drive_folder_id"),
                rs.getString("drive_folder_name"),
                rs.getString("redmine_project_id"),
                rs.getString("redmine_project_name"),
                VerificationScope.valueOf(rs.getString("scope")),
                rs.getObject("macro_activity_id", UUID.class),
                rs.getString("note"),
                rs.getString("status"),
                rs.getObject("started_by", UUID.class),
                startedAt.toInstant(),
                selections,
                rs.getInt("total")
        );
    }

    @Override
    public Optional<VerificationRunSnapshot> findById(UUID id) {
        List<VerificationRunSnapshot> rows = jdbc.query(
                RUN_SELECT + " WHERE id = ?",
                this::mapSnapshot,
                id
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(withSelections(rows.getFirst(), loadTicketSelections(List.of(id)).get(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VerificationRunSnapshot> list(UUID serviceId, int page, int size) {
        Long total = serviceId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM verification_runs", Long.class)
                : jdbc.queryForObject(
                        "SELECT COUNT(*) FROM verification_runs WHERE service_id = ?",
                        Long.class,
                        serviceId
                );
        long offset = Math.multiplyExact((long) page, size);
        String orderAndPage = " ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?";
        List<VerificationRunSnapshot> rows = serviceId == null
                ? jdbc.query(RUN_SELECT + orderAndPage, this::mapSnapshot, size, offset)
                : jdbc.query(
                        RUN_SELECT + " WHERE service_id = ?" + orderAndPage,
                        this::mapSnapshot,
                        serviceId,
                        size,
                        offset
                );
        Map<UUID, ValidatedTicketSelections> selectionsByRun = loadTicketSelections(
                rows.stream().map(VerificationRunSnapshot::id).toList()
        );
        List<VerificationRunSnapshot> items = rows.stream()
                .map(run -> withSelections(run, selectionsByRun.get(run.id())))
                .toList();
        return new Page<>(items, page, size, total == null ? 0 : total);
    }

    private VerificationRunSnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
        Timestamp finishedAt = rs.getTimestamp("finished_at");
        return new VerificationRunSnapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("service_id", UUID.class),
                ServiceType.valueOf(rs.getString("service_type_snapshot")),
                rs.getInt("service_revision_snapshot"),
                rs.getObject("process_version_id", UUID.class),
                rs.getString("drive_folder_id"),
                rs.getString("drive_folder_name"),
                rs.getString("redmine_project_id"),
                rs.getString("redmine_project_name"),
                VerificationScope.valueOf(rs.getString("scope")),
                rs.getObject("macro_activity_id", UUID.class),
                rs.getString("note"),
                rs.getString("status"),
                rs.getObject("started_by", UUID.class),
                rs.getTimestamp("started_at").toInstant(),
                finishedAt == null ? null : finishedAt.toInstant(),
                rs.getString("failure_message"),
                rs.getInt("total"),
                new ValidatedTicketSelections(List.of())
        );
    }

    private Map<UUID, ValidatedTicketSelections> loadTicketSelections(List<UUID> runIds) {
        if (runIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(runIds.size(), "?"));
        Map<UUID, Map<String, List<String>>> ticketsByRun = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT run_id, group_id, ticket_id
                FROM verification_ticket_selections
                WHERE run_id IN (%s)
                ORDER BY run_id, group_id, ticket_id
                """.formatted(placeholders),
                (RowCallbackHandler) rs -> ticketsByRun
                        .computeIfAbsent(rs.getObject("run_id", UUID.class), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(rs.getString("group_id"), ignored -> new ArrayList<>())
                        .add(rs.getString("ticket_id")),
                runIds.toArray()
        );
        Map<UUID, ValidatedTicketSelections> selectionsByRun = new LinkedHashMap<>();
        for (UUID runId : runIds) {
            Map<String, List<String>> ticketsByGroup = ticketsByRun.getOrDefault(runId, Map.of());
            selectionsByRun.put(runId, new ValidatedTicketSelections(ticketsByGroup.entrySet().stream()
                    .map(entry -> new ValidatedTicketSelection(
                            entry.getKey(),
                            UUID.fromString(entry.getKey().substring("ticket-group:".length())),
                            entry.getValue()
                    ))
                    .toList()));
        }
        return selectionsByRun;
    }

    private VerificationRunSnapshot withSelections(
            VerificationRunSnapshot run,
            ValidatedTicketSelections selections
    ) {
        return new VerificationRunSnapshot(
                run.id(), run.serviceId(), run.serviceTypeSnapshot(), run.serviceRevisionSnapshot(),
                run.processVersionId(), run.driveFolderId(), run.driveFolderName(), run.redmineProjectId(),
                run.redmineProjectName(), run.scope(), run.macroActivityId(), run.note(), run.status(),
                run.startedBy(), run.startedAt(), run.finishedAt(), run.failureMessage(), run.total(),
                selections
        );
    }

    @Override
    public boolean markRunning(UUID id) {
        return jdbc.update("""
                UPDATE verification_runs SET status = 'RUNNING'::run_status
                WHERE id = ? AND status = 'QUEUED'::run_status
                """, id) == 1;
    }

    @Override
    public boolean markCompleted(UUID id) {
        return jdbc.update("""
                UPDATE verification_runs SET status = 'COMPLETED'::run_status, finished_at = NOW()
                WHERE id = ? AND status = 'RUNNING'::run_status
                  AND total = (SELECT COUNT(*) FROM verification_results WHERE run_id = ?)
                """, id, id) == 1;
    }

    @Override
    public void markFailed(UUID id, String safeMessage) {
        jdbc.update("""
                UPDATE verification_runs
                SET status = 'FAILED'::run_status, finished_at = NOW(), failure_message = ?
                WHERE id = ? AND status IN ('QUEUED'::run_status, 'RUNNING'::run_status)
                """, safeMessage, id);
    }

    @Override
    public int failInterruptedRuns() {
        return jdbc.update("""
                UPDATE verification_runs
                SET status = 'FAILED'::run_status, finished_at = NOW(),
                    failure_message = 'La verificación se interrumpió al reiniciar el backend.'
                WHERE status IN ('QUEUED'::run_status, 'RUNNING'::run_status)
                """);
    }
}
