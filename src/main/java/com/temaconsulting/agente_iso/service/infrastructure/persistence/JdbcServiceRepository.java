package com.temaconsulting.agente_iso.service.infrastructure.persistence;

import com.temaconsulting.agente_iso.service.application.port.ServiceRepository;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.service.domain.Service;
import com.temaconsulting.agente_iso.service.domain.ServiceProcessAssignment;
import com.temaconsulting.agente_iso.service.domain.ServiceSources;
import com.temaconsulting.agente_iso.service.domain.ServiceStatus;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcServiceRepository implements ServiceRepository {
    private final JdbcTemplate jdbc;

    public JdbcServiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Service> list(int page, int size) {
        List<Service> items = jdbc.query(
                "SELECT * FROM services ORDER BY created_at ASC, id ASC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapService(rs), size, Math.multiplyExact(page, size)
        );
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM services", Long.class);
        return new Page<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Service> findById(UUID id) {
        return jdbc.query("SELECT * FROM services WHERE id = ?", (rs, rowNum) -> mapService(rs), id)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public void insert(Service service) {
        jdbc.update(
                """
                INSERT INTO services (
                    id, name, client, service_type, status, revision,
                    drive_folder_id, drive_folder_name, redmine_project_id, redmine_project_name,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?::service_type, ?::service_status, ?, ?, ?, ?, ?, ?, ?)
                """,
                service.id(), service.name(), service.client(), service.serviceType().name(),
                service.status().name(), service.revision(),
                id(service.sources().driveFolder()), name(service.sources().driveFolder()),
                id(service.sources().redmineProject()), name(service.sources().redmineProject()),
                Timestamp.from(service.createdAt()), Timestamp.from(service.updatedAt())
        );
        insertAssignments(service);
    }

    @Override
    @Transactional
    public boolean replace(Service service, int expectedRevision) {
        int updated = jdbc.update(
                """
                UPDATE services SET
                    name = ?, client = ?, service_type = ?::service_type,
                    status = ?::service_status, revision = ?, drive_folder_id = ?,
                    drive_folder_name = ?, redmine_project_id = ?, redmine_project_name = ?, updated_at = ?
                WHERE id = ? AND revision = ?
                """,
                service.name(), service.client(), service.serviceType().name(), service.status().name(),
                service.revision(), id(service.sources().driveFolder()), name(service.sources().driveFolder()),
                id(service.sources().redmineProject()), name(service.sources().redmineProject()),
                Timestamp.from(service.updatedAt()), service.id(), expectedRevision
        );
        if (updated == 0) {
            return false;
        }
        jdbc.update("DELETE FROM service_process_versions WHERE service_id = ?", service.id());
        insertAssignments(service);
        return true;
    }

    private Service mapService(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        ExternalSource drive = source(rs.getString("drive_folder_id"), rs.getString("drive_folder_name"));
        ExternalSource redmine = source(rs.getString("redmine_project_id"), rs.getString("redmine_project_name"));
        List<ServiceProcessAssignment> assignments = jdbc.query(
                """
                SELECT process_id, process_version_id
                FROM service_process_versions WHERE service_id = ?
                ORDER BY process_id
                """,
                (association, rowNum) -> new ServiceProcessAssignment(
                        association.getObject("process_id", UUID.class),
                        association.getObject("process_version_id", UUID.class)
                ), id
        );
        return new Service(
                id, rs.getString("name"), rs.getString("client"),
                ServiceType.valueOf(rs.getString("service_type")),
                ServiceStatus.valueOf(rs.getString("status")), rs.getInt("revision"),
                assignments, new ServiceSources(drive, redmine),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()
        );
    }

    private void insertAssignments(Service service) {
        for (ServiceProcessAssignment assignment : service.processes()) {
            jdbc.update(
                    "INSERT INTO service_process_versions (service_id, process_id, process_version_id) VALUES (?, ?, ?)",
                    service.id(), assignment.processId(), assignment.processVersionId()
            );
        }
    }

    private static ExternalSource source(String id, String name) {
        return id == null ? null : new ExternalSource(id, name);
    }

    private static String id(ExternalSource source) {
        return source == null ? null : source.id();
    }

    private static String name(ExternalSource source) {
        return source == null ? null : source.name();
    }
}
