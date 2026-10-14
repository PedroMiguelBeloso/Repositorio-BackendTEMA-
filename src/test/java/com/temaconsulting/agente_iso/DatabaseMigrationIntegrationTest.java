package com.temaconsulting.agente_iso;

import com.temaconsulting.agente_iso.verification.application.VerificationRunDraft;
import com.temaconsulting.agente_iso.verification.application.VerificationStartFailure;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelection;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.infrastructure.JdbcVerificationRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class DatabaseMigrationIntegrationTest {

    // Levanta una base vacía en Docker antes de correr los tests
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    // Inyecta dinámicamente las credenciales del contenedor a Spring Boot
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcVerificationRunRepository verificationRuns;

    @Test
    void testFlywayMigrationsExecuteSuccessfully() {
        // Verifica que Flyway corrió exitosamente y guardó el historial
        Integer migrationsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        
        assertThat(migrationsCount).isGreaterThanOrEqualTo(4); // V1, V2, V3 y V4 completadas
    }

    @Test
    void testConstraint_uq_verification_results_without_ticket() {
        // 1. Insertar dependencias mockeadas necesarias para verification_results
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, email, display_name, password_hash, role, active) VALUES (?, ?, ?, ?, ?::role_enum, ?)",
                userId, "test@test.com", "Test User", "hash", "ADMIN", true);

        UUID registryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO registry_definitions (id, key, canonical_name, evidence_type) VALUES (?, ?, ?, ?::evidence_type)",
                registryId, "REG-1", "Registro 1", "DOCUMENT");

        UUID processId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO processes (id, code, name) VALUES (?, ?::process_code, ?)",
                processId, "START_UP", "Start Up");

        UUID processVersionId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO process_versions " +
                        "(id, process_id, official_version, objective, owner, version_date, status, revision) " +
                        "VALUES (?, ?, ?, ?, ?, NOW(), ?::process_status, ?)",
                processVersionId,
                processId,
                "v1",
                "Obj",
                "Owner",
                "DRAFT",
                0
        );

        UUID macroId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO macro_activities " +
                        "(id, process_version_id, name, \"order\") VALUES (?, ?, ?, ?)",
                macroId,
                processVersionId,
                "Macro 1",
                1
        );

        UUID expectationId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO evidence_expectations " +
                        "(id, macro_activity_id, registry_definition_id, display_name, applicability, cardinality, location, \"order\") " +
                        "VALUES (?, ?, ?, ?, ?::applicability_mode, ?::cardinality_enum, ?::evidence_location, ?)",
                expectationId,
                macroId,
                registryId,
                "Exp 1",
                "ALWAYS",
                "ONE",
                "INPUT",
                1
        );

// Una vez armado el árbol, la versión se publica.
        jdbcTemplate.update(
                "UPDATE process_versions " +
                        "SET status = 'PUBLISHED'::process_status, published_at = NOW(), revision = revision + 1 " +
                        "WHERE id = ? AND status = 'DRAFT'::process_status",
                processVersionId
        );

        UUID serviceId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO services (id, name, client, service_type, status, revision) VALUES (?, ?, ?, ?::service_type, ?::service_status, ?)",
                serviceId, "Servicio 1", "Cliente A", "SUPPORT_ERP", "ACTIVE", 1);

        jdbcTemplate.update("INSERT INTO service_process_versions (service_id, process_id, process_version_id) VALUES (?, ?, ?)",
                 serviceId, processId, processVersionId);

        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO verification_runs (id, service_id, process_version_id, started_by, service_type_snapshot, service_revision_snapshot, scope, status) VALUES (?, ?, ?, ?, ?::service_type, ?, ?::verification_scope, ?::run_status)",
                runId, serviceId, processVersionId, userId, "SUPPORT_ERP", 1, "PROCESS", "QUEUED");

        // 2. Insertar un resultado exitoso sin ticket
        UUID resultId1 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO verification_results (id, run_id, expectation_id, macro_activity_id, display_name, proposed_status, effective_status, applicability_decision, applicability_origin, rationale, revision) VALUES (?, ?, ?, ?, ?, ?::result_status, ?::result_status, ?::applicability_decision, ?::decision_origin, ?, ?)",
            resultId1, runId, expectationId, macroId, "Display 1", "COMPLIES", "COMPLIES", "APPLIES", "DETERMINISTIC", "rationale", 1);

        // 3. Intentar insertar otro resultado para la MISMA ejecución y expectativa, también sin ticket.
        // Esto DEBE fallar debido al índice único parcial uq_verification_results_without_ticket creado en V2.
        UUID resultId2 = UUID.randomUUID();
        
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("INSERT INTO verification_results (id, run_id, expectation_id, macro_activity_id, display_name, proposed_status, effective_status, applicability_decision, applicability_origin, rationale, revision) VALUES (?, ?, ?, ?, ?, ?::result_status, ?::result_status, ?::applicability_decision, ?::decision_origin, ?, ?)",
                resultId2, runId, expectationId, macroId, "Display 2", "COMPLIES", "COMPLIES", "APPLIES", "DETERMINISTIC", "rationale 2", 1);
        });
    }
    @Test
    void testUsersEmailUniquenessIsCaseInsensitiveAtDatabaseLevel() {
        UUID firstId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, display_name, password_hash, role, active) VALUES (?, ?, ?, ?, ?::role_enum, ?)",
                firstId, "Case@Test.com", "First", "hash", "READER", true
        );

        UUID secondId = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO users (id, email, display_name, password_hash, role, active) VALUES (?, ?, ?, ?, ?::role_enum, ?)",
                secondId, "case@test.com", "Second", "hash", "READER", true
        ));
    }

    @Test
    void persistsExactlyTheValidatedTicketIdsForTheCreatedRun() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, display_name, password_hash, role, active) VALUES (?, ?, ?, ?, ?::role_enum, ?)",
                userId, userId + "@test.com", "Admin", "hash", "ADMIN", true
        );
        UUID processId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processes (id, code, name, m2_enabled) VALUES (?, ?::process_code, ?, ?)",
                processId, "TAKE_OVER", "Take Over", true
        );
        UUID processVersionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO process_versions (
                    id, process_id, official_version, objective, owner, version_date,
                    status, revision, published_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?::process_status, ?, NOW())
                """,
                processVersionId, processId, "v1", "Objective", "Owner", "PUBLISHED", 1
        );
        UUID serviceId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO services (
                    id, name, client, service_type, status, revision,
                    redmine_project_id, redmine_project_name
                ) VALUES (?, ?, ?, ?::service_type, ?::service_status, ?, ?, ?)
                """,
                serviceId, "Service", "Client", "SOFTWARE_FACTORY", "ACTIVE", 3,
                "redmine-project-1", "Project"
        );
        jdbcTemplate.update(
                "INSERT INTO service_process_versions (service_id, process_id, process_version_id) VALUES (?, ?, ?)",
                serviceId, processId, processVersionId
        );

        UUID runId = UUID.randomUUID();
        UUID macroActivityId = UUID.randomUUID();
        var selected = new ValidatedTicketSelections(List.of(
                new ValidatedTicketSelection(
                        "ticket-group:" + macroActivityId,
                        macroActivityId,
                        List.of("204", "205")
                )
        ));
        var created = verificationRuns.create(new VerificationRunDraft(
                runId, serviceId, 3, processVersionId, userId,
                VerificationScope.PROCESS, null, "Start note", selected, 2
        ));

        assertThat(created.id()).isEqualTo(runId);
        assertThat(created.serviceRevisionSnapshot()).isEqualTo(3);
        assertThat(created.total()).isEqualTo(2);
        assertThat(verificationRuns.findById(runId).orElseThrow().total()).isEqualTo(2);
        assertThat(created.redmineProjectId()).isEqualTo("redmine-project-1");
        assertThat(created.ticketSelections().groups().getFirst().ticketIds())
                .containsExactly("204", "205");
        List<String> savedTicketIds = jdbcTemplate.queryForList(
                """
                SELECT ticket_id FROM verification_ticket_selections
                WHERE run_id = ? AND group_id = ? ORDER BY ticket_id
                """,
                String.class, runId, "ticket-group:" + macroActivityId
        );
        assertThat(savedTicketIds).containsExactly("204", "205");

        UUID laterRunId = UUID.randomUUID();
        var laterSelection = new ValidatedTicketSelections(List.of(
                new ValidatedTicketSelection(
                        "ticket-group:" + macroActivityId, macroActivityId, List.of("206")
                )
        ));
        verificationRuns.create(new VerificationRunDraft(
                laterRunId, serviceId, 3, processVersionId, userId,
                VerificationScope.PROCESS, null, null, laterSelection, 1
        ));
        assertThat(jdbcTemplate.queryForList(
                "SELECT ticket_id FROM verification_ticket_selections WHERE run_id = ?",
                String.class, runId
        )).containsExactlyInAnyOrder("204", "205");
        assertThat(jdbcTemplate.queryForList(
                "SELECT ticket_id FROM verification_ticket_selections WHERE run_id = ?",
                String.class, laterRunId
        )).containsExactly("206");

        UUID staleRunId = UUID.randomUUID();
        assertThatThrownBy(() -> verificationRuns.create(new VerificationRunDraft(
                staleRunId, serviceId, 2, processVersionId, userId,
                VerificationScope.PROCESS, null, null, selected, 2
        )))
                .isInstanceOf(VerificationStartFailure.class)
                .hasMessageContaining("Service configuration changed");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verification_runs WHERE id = ?",
                Integer.class, staleRunId
        )).isZero();
    }

}
