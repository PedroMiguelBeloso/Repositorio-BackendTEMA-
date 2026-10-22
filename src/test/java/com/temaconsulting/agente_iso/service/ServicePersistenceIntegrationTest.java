package com.temaconsulting.agente_iso.service;

import com.temaconsulting.agente_iso.service.application.ServiceConfigurationService;
import com.temaconsulting.agente_iso.service.application.ServiceQueryService;
import com.temaconsulting.agente_iso.service.application.port.ServiceRepository;
import com.temaconsulting.agente_iso.service.application.model.ServiceWriteCommand;
import com.temaconsulting.agente_iso.service.domain.Service;
import com.temaconsulting.agente_iso.service.domain.ServiceProcessAssignment;
import com.temaconsulting.agente_iso.service.domain.ServiceSources;
import com.temaconsulting.agente_iso.service.domain.ServiceStatus;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ServicePersistenceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ServiceConfigurationService configuration;

    @Autowired
    ServiceQueryService queries;

    @Autowired
    ServiceRepository repository;

    @Test
    void createsListsAndReplacesServiceAndAssociationsWithOptimisticRevision() {
        UUID startUp = version("START_UP", "Start Up", "PUBLISHED");
        UUID takeOver = version("TAKE_OVER", "Take Over", "PUBLISHED");

        var created = configuration.create(command(null, List.of(startUp), null));
        UUID id = created.service().id();
        assertThat(created.service().revision()).isEqualTo(1);
        assertThat(created.processes()).extracting("processName").containsExactly("Start Up");
        assertThat(queries.list(0, 20).items()).extracting(view -> view.service().id()).contains(id);
        assertThat(queries.verifiableProcesses(id)).hasSize(1);

        var updated = configuration.update(id, command(1, List.of(takeOver), null));
        assertThat(updated.service().revision()).isEqualTo(2);
        assertThat(updated.processes()).extracting("processName").containsExactly("Take Over");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_process_versions WHERE service_id = ?", Integer.class, id
        )).isEqualTo(1);

        assertThatThrownBy(() -> configuration.update(id, command(1, List.of(startUp), null)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).httpStatus().value()).isEqualTo(409));
        assertThat(queries.get(id).service().revision()).isEqualTo(2);
    }

    @Test
    void rejectsDraftQualityAndTwoVersionsOfTheSameProcess() {
        UUID processId = process("START_UP", "Start Up");
        UUID published = versionFor(processId, "v1", "PUBLISHED");
        UUID secondPublished = versionFor(processId, "v2", "PUBLISHED");
        UUID draft = version("TAKE_OVER", "Take Over", "DRAFT");
        UUID quality = version("QUALITY_CONTINUOUS_IMPROVEMENT", "Calidad", "PUBLISHED");

        assertInvalid(() -> configuration.create(command(null, List.of(draft), null)));
        assertInvalid(() -> configuration.create(command(null, List.of(quality), null)));
        assertInvalid(() -> configuration.create(command(null, List.of(published, secondPublished), null)));
        assertInvalid(() -> configuration.create(command(null, List.of(published, published), null)));
    }

    @Test
    void rejectsUnvalidatedSourcesWithoutPersistingAService() {
        assertThatThrownBy(() -> configuration.create(command(null, List.of(), "drive-id")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).httpStatus().value()).isEqualTo(503));
    }

    @Test
    void databaseRollsBackServiceIfAssociationViolatesPublicationRule() {
        UUID draft = version("START_UP", "Start Up", "DRAFT");
        UUID processId = jdbc.queryForObject(
                "SELECT process_id FROM process_versions WHERE id = ?", UUID.class, draft
        );
        UUID serviceId = UUID.randomUUID();
        Service invalid = Service.create(
                serviceId, "Rollback example", "Cliente", ServiceType.SUPPORT_ERP,
                ServiceStatus.ACTIVE, List.of(new ServiceProcessAssignment(processId, draft)),
                new ServiceSources(null, null), Instant.now()
        );
        assertThatThrownBy(() -> repository.insert(invalid)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM services WHERE id = ?", Integer.class, serviceId))
                .isZero();
    }

    private void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).httpStatus().value()).isEqualTo(422));
    }

    private ServiceWriteCommand command(Integer revision, List<UUID> versions, String driveId) {
        return new ServiceWriteCommand(
                revision, "Servicio de prueba", "Cliente de prueba", ServiceType.SUPPORT_ERP,
                ServiceStatus.ACTIVE, versions, driveId, null
        );
    }

    private UUID version(String code, String name, String status) {
        return versionFor(process(code, name), "v1", status);
    }

    private UUID process(String code, String name) {
        List<UUID> existing = jdbc.query(
                "SELECT id FROM processes WHERE code = ?::process_code",
                (rs, rowNum) -> rs.getObject("id", UUID.class), code
        );
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO processes (id, code, name, m2_enabled) VALUES (?, ?::process_code, ?, ?)",
                id, code, name, !code.equals("QUALITY_CONTINUOUS_IMPROVEMENT"));
        return id;
    }

    private UUID versionFor(UUID processId, String label, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO process_versions (
                    id, process_id, official_version, objective, owner, version_date,
                    status, revision, published_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?::process_status, 1, ?)
                """,
                id, processId, label + "-" + id.toString().substring(0, 8), "Objetivo", "Responsable", status,
                status.equals("PUBLISHED") ? java.sql.Timestamp.from(Instant.now()) : null
        );
        return id;
    }
}
