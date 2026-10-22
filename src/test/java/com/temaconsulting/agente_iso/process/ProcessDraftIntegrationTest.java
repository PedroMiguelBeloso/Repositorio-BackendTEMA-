package com.temaconsulting.agente_iso.process;

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
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "app.cors.allowed-origin=*")
@Testcontainers
class ProcessDraftIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void testOptimisticLockingFailsOnStaleRevision() {
        UUID processId = UUID.randomUUID();
        jdbc.update("INSERT INTO processes (id, code, name) VALUES (?, 'START_UP'::process_code, 'Test Process')", processId);
        
        UUID versionId = UUID.randomUUID();
        jdbc.update("INSERT INTO process_versions (id, process_id, official_version, objective, owner, version_date, status, revision) VALUES (?, ?, 'v1', 'Obj', 'Owner', NOW(), 'DRAFT'::process_status, 2)", versionId, processId);

        int rows = jdbc.update("UPDATE process_versions SET revision = revision + 1 WHERE id = ? AND revision = 1 AND status = 'DRAFT'::process_status", versionId);
        
        assertThat(rows).isEqualTo(0);
    }

    @Test
    void testAcceptanceCriteriaInsertExpectations() {
        UUID registryId = UUID.randomUUID();
        jdbc.update("INSERT INTO registry_definitions (id, key, canonical_name, evidence_type) VALUES (?, 'TICK-1', 'Ticket Test', 'TICKET'::evidence_type)", registryId);
        
        UUID processId = UUID.randomUUID();
        jdbc.update("INSERT INTO processes (id, code, name) VALUES (?, 'TAKE_OVER'::process_code, 'P2')", processId);
        UUID versionId = UUID.randomUUID();
        jdbc.update("INSERT INTO process_versions (id, process_id, official_version, objective, owner, version_date, status) VALUES (?, ?, 'v1', 'O', 'Ow', NOW(), 'DRAFT'::process_status)", versionId, processId);
        UUID macroId = UUID.randomUUID();
        jdbc.update("INSERT INTO macro_activities (id, process_version_id, name, \"order\") VALUES (?, ?, 'M1', 1)", macroId, versionId);

        jdbc.update("INSERT INTO evidence_expectations (id, macro_activity_id, registry_definition_id, display_name, applicability, cardinality, location, \"order\") " +
                    "VALUES (?, ?, ?, 'Entrada Tickets', 'ALWAYS'::applicability_mode, 'MULTIPLE'::cardinality_enum, 'INPUT'::evidence_location, 1)", 
                    UUID.randomUUID(), macroId, registryId);

        jdbc.update("INSERT INTO evidence_expectations (id, macro_activity_id, registry_definition_id, display_name, applicability, cardinality, location, \"order\") " +
                    "VALUES (?, ?, ?, 'Orden Transporte', 'CASE_DEPENDENT'::applicability_mode, 'ONE'::cardinality_enum, 'OUTPUT'::evidence_location, 2)", 
                    UUID.randomUUID(), macroId, registryId);

        Integer count = jdbc.queryForObject("SELECT count(*) FROM evidence_expectations WHERE macro_activity_id = ?", Integer.class, macroId);
        assertThat(count).isEqualTo(2);
    }
}