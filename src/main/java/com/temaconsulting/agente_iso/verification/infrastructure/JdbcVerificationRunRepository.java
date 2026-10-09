package com.temaconsulting.agente_iso.verification.infrastructure;

import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.application.CreatedVerificationRun;
import com.temaconsulting.agente_iso.verification.application.VerificationRunDraft;
import com.temaconsulting.agente_iso.verification.application.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.VerificationStartFailure;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelection;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Repository
public class JdbcVerificationRunRepository implements VerificationRunRepository {
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
                    macro_activity_id, note, status
                ) VALUES (?, ?, ?, ?, NULL, ?, ?::verification_scope, ?, ?, 'QUEUED'::run_status)
                """,
                run.id(), run.serviceId(), run.processVersionId(), run.startedBy(),
                run.serviceRevision(), run.scope().name(), run.macroActivityId(), run.note()
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
                       started_by, started_at
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
                selections
        );
    }
}
