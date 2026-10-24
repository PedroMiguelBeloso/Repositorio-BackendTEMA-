package com.temaconsulting.agente_iso.process.infrastructure.persistence;

import com.temaconsulting.agente_iso.process.application.port.ProcessRepository;
import com.temaconsulting.agente_iso.process.application.port.ProcessVersionRepository;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Two JDBC connections allow Flyway startup but expose deeply nested RowMapper queries.
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=1000"
})
@Testcontainers
class JdbcProcessPersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ProcessRepository processes;

    @Autowired
    private ProcessVersionRepository versions;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM process_versions");
        jdbc.update("DELETE FROM processes");
    }

    @Test
    void createsAndReadsProcessAndDraftFromPostgreSql() {
        UUID processId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-26T15:00:00Z");

        ProcessDefinition process = new ProcessDefinition(
                processId,
                ProcessCode.START_UP,
                "Start Up",
                true,
                createdAt
        );

        processes.insert(process);

        ProcessDefinition loadedProcess = processes.findById(processId).orElseThrow();
        assertThat(loadedProcess.code()).isEqualTo(ProcessCode.START_UP);
        assertThat(loadedProcess.m2Enabled()).isTrue();
        assertThat(processes.list(0, 20).items())
                .extracting(ProcessDefinition::id)
                .contains(processId);

        UUID versionId = UUID.randomUUID();
        ProcessVersion draft = new ProcessVersion(
                versionId,
                processId,
                "2.0",
                LocalDate.of(2026, 9, 26),
                ProcessStatus.DRAFT,
                0,
                "",
                "",
                null,
                createdAt
        );

        versions.insertDraft(draft);

        ProcessVersion loadedDraft = versions.findWithTree(versionId).orElseThrow();
        assertThat(loadedDraft.processId()).isEqualTo(processId);
        assertThat(loadedDraft.officialVersion()).isEqualTo("2.0");
        assertThat(loadedDraft.status()).isEqualTo(ProcessStatus.DRAFT);
        assertThat(loadedDraft.revision()).isZero();
        jdbc.update(
                "INSERT INTO macro_activities (id, process_version_id, name, description, \"order\") VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), versionId, "Organización interna", null, 1
        );
        assertThat(versions.listByProcess(processId, 0, 20).items())
                .extracting(ProcessVersion::id)
                .contains(versionId);
        assertThat(versions.listByProcess(processId, 0, 20).items().getFirst().macroActivities())
                .hasSize(1);

        Integer databaseRevision = jdbc.queryForObject(
                "SELECT revision FROM process_versions WHERE id = ?",
                Integer.class,
                versionId
        );
        assertThat(databaseRevision).isZero();
    }
}
