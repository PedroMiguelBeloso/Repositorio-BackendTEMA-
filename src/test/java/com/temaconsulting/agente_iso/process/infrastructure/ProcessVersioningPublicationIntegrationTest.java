package com.temaconsulting.agente_iso.process.infrastructure;

import com.temaconsulting.agente_iso.process.api.dto.ActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.CreateVersionRequest;
import com.temaconsulting.agente_iso.process.api.dto.EvidenceExpectationInput;
import com.temaconsulting.agente_iso.process.api.dto.MacroActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.ProcessPolicyInput;
import com.temaconsulting.agente_iso.process.api.dto.SaveDraftRequest;
import com.temaconsulting.agente_iso.process.application.ProcessDraftService;
import com.temaconsulting.agente_iso.process.application.ProcessPublicationService;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.Page;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.cors.allowed-origin=*")
@Testcontainers
class ProcessVersioningPublicationIntegrationTest {

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

    @Autowired
    private ProcessDraftService drafts;

    @Autowired
    private ProcessPublicationService publications;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE TABLE
                    verification_result_evidence,
                    verification_result_limitations,
                    human_decisions,
                    verification_results,
                    verification_ticket_selections,
                    verification_runs,
                    service_process_versions,
                    services,
                    evidence_expectations,
                    activities,
                    macro_activities,
                    process_policies,
                    process_versions,
                    processes,
                    registry_definitions
                CASCADE
                """);
    }

    @Test
    void publishesCopiesAndKeepsSourceImmutableWithFreshIds() {
        UUID processId = insertProcess("START_UP", true);
        UUID registryId = insertRegistry("TICKET", null);

        ProcessVersion draft = drafts.createDraft(
                processId,
                new CreateVersionRequest("1.0", LocalDate.of(2026, 9, 30), null)
        );
        ProcessVersion saved = drafts.saveDraft(processId, draft.id(), validRequest(draft.revision(), "1.0", registryId));

        ProcessVersion published = publications.publish(processId, saved.id(), saved.revision());

        assertThat(published.status()).isEqualTo(ProcessStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.revision()).isEqualTo(saved.revision() + 1);
        assertThat(published.macroActivities()).hasSize(1);
        assertThat(published.macroActivities().get(0).inputs().get(0).evidenceType().name()).isEqualTo("TICKET");

        ProcessVersion copy = drafts.createDraft(
                processId,
                new CreateVersionRequest("2.0", LocalDate.of(2026, 10, 1), published.id())
        );

        assertThat(copy.status()).isEqualTo(ProcessStatus.DRAFT);
        assertThat(copy.revision()).isZero();
        assertThat(copy.publishedAt()).isNull();
        assertThat(copy.objective()).isEqualTo(published.objective());
        assertThat(copy.owner()).isEqualTo(published.owner());

        assertThat(copy.id()).isNotEqualTo(published.id());
        assertThat(copy.policies().get(0).id()).isNotEqualTo(published.policies().get(0).id());
        assertThat(copy.macroActivities().get(0).id()).isNotEqualTo(published.macroActivities().get(0).id());
        assertThat(copy.macroActivities().get(0).activities().get(0).id())
                .isNotEqualTo(published.macroActivities().get(0).activities().get(0).id());
        assertThat(copy.macroActivities().get(0).inputs().get(0).id())
                .isNotEqualTo(published.macroActivities().get(0).inputs().get(0).id());
        assertThat(copy.macroActivities().get(0).inputs().get(0).registryDefinitionId())
                .isEqualTo(registryId);

        ProcessVersion sourceAgain = drafts.getVersion(processId, published.id());
        assertThat(sourceAgain).isEqualTo(published);

        Page<ProcessVersion> history = drafts.listVersions(processId, 0, 1);
        assertThat(history.items()).hasSize(1);
        assertThat(history.totalElements()).isEqualTo(2);
        assertThat(history.items().get(0).macroActivities()).isNotEmpty();
    }

    @Test
    void invalidPublicationReturns422AndLeavesDraftUnchanged() {
        UUID processId = insertProcess("TAKE_OVER", true);
        ProcessVersion draft = drafts.createDraft(
                processId,
                new CreateVersionRequest("1.0", LocalDate.of(2026, 9, 30), null)
        );

        assertThatThrownBy(() -> publications.publish(processId, draft.id(), draft.revision()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.code()).isEqualTo(ProblemCode.VALIDATION_ERROR);
                });

        ProcessVersion unchanged = drafts.getVersion(processId, draft.id());
        assertThat(unchanged.status()).isEqualTo(ProcessStatus.DRAFT);
        assertThat(unchanged.publishedAt()).isNull();
        assertThat(unchanged.revision()).isEqualTo(draft.revision());
    }

    @Test
    void staleRepublishAndWritesToPublishedVersionAreRejected() {
        UUID processId = insertProcess("SUPPORT_SOFTWARE_FACTORY", true);
        UUID registryId = insertRegistry("TICKET", null);
        ProcessVersion draft = drafts.createDraft(
                processId,
                new CreateVersionRequest("1.0", LocalDate.of(2026, 9, 30), null)
        );
        ProcessVersion saved = drafts.saveDraft(processId, draft.id(), validRequest(0, "1.0", registryId));

        assertThatThrownBy(() -> publications.publish(processId, saved.id(), saved.revision() - 1))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ProblemCode.STALE_REVISION));

        ProcessVersion published = publications.publish(processId, saved.id(), saved.revision());

        assertThatThrownBy(() -> publications.publish(processId, published.id(), published.revision()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.httpStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> drafts.saveDraft(
                processId,
                published.id(),
                validRequest(published.revision(), "1.0", registryId)
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.httpStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE process_policies SET text = 'changed' WHERE process_version_id = ?",
                published.id()
        )).isInstanceOf(DataAccessException.class);

        ProcessVersion unchanged = drafts.getVersion(processId, published.id());
        assertThat(unchanged).isEqualTo(published);
    }

    @Test
    void wrongProcessPathReturns404AndQualityCanPublishInM1() {
        UUID qualityId = insertProcess("QUALITY_CONTINUOUS_IMPROVEMENT", false);
        UUID anotherProcessId = insertProcess("START_UP", true);
        UUID registryId = insertRegistry("TICKET", null);

        ProcessVersion draft = drafts.createDraft(
                qualityId,
                new CreateVersionRequest("1.0", LocalDate.of(2026, 9, 30), null)
        );
        ProcessVersion saved = drafts.saveDraft(qualityId, draft.id(), validRequest(0, "1.0", registryId));
        ProcessVersion published = publications.publish(qualityId, saved.id(), saved.revision());

        assertThat(published.status()).isEqualTo(ProcessStatus.PUBLISHED);

        assertThatThrownBy(() -> drafts.getVersion(anotherProcessId, published.id()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void copyFailureRollsBackRootAndTree() {
        UUID processId = insertProcess("START_UP", true);
        UUID registryId = insertRegistry("TICKET", null);
        ProcessVersion sourceDraft = drafts.createDraft(
                processId,
                new CreateVersionRequest("1.0", LocalDate.of(2026, 9, 30), null)
        );
        ProcessVersion sourceSaved = drafts.saveDraft(
                processId,
                sourceDraft.id(),
                validRequest(0, "1.0", registryId)
        );
        ProcessVersion source = publications.publish(processId, sourceSaved.id(), sourceSaved.revision());

        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_tc149_policy_copy()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  RAISE EXCEPTION 'forced copy failure';
                END;
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER trg_fail_tc149_policy_copy
                BEFORE INSERT ON process_policies
                FOR EACH ROW EXECUTE FUNCTION fail_tc149_policy_copy()
                """);

        try {
            assertThatThrownBy(() -> drafts.createDraft(
                    processId,
                    new CreateVersionRequest("2.0", LocalDate.of(2026, 10, 1), source.id())
            )).isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_tc149_policy_copy ON process_policies");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_tc149_policy_copy()");
        }

        Integer copies = jdbc.queryForObject(
                "SELECT count(*) FROM process_versions WHERE process_id = ? AND official_version = '2.0'",
                Integer.class,
                processId
        );
        assertThat(copies).isZero();
    }

    private UUID insertProcess(String code, boolean m2Enabled) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO processes (id, code, name, m2_enabled) VALUES (?, ?::process_code, ?, ?)",
                id,
                code,
                code,
                m2Enabled
        );
        return id;
    }

    private UUID insertRegistry(String type, String nomenclature) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO registry_definitions (id, key, canonical_name, evidence_type, official_nomenclature, preloaded) " +
                        "VALUES (?, ?, ?, ?::evidence_type, ?, true)",
                id,
                "REG-" + id,
                "Registry " + id,
                type,
                nomenclature
        );
        return id;
    }

    private SaveDraftRequest validRequest(int revision, String officialVersion, UUID registryId) {
        return new SaveDraftRequest(
                revision,
                officialVersion,
                LocalDate.of(2026, 9, 30),
                "Objective",
                "Owner",
                List.of(new ProcessPolicyInput(null, 0, "Policy")),
                List.of(new MacroActivityInput(
                        null,
                        "Macro",
                        "Description",
                        0,
                        List.of(new EvidenceExpectationInput(
                                null,
                                registryId,
                                "Ticket input",
                                ApplicabilityMode.ALWAYS,
                                Cardinality.ONE,
                                0
                        )),
                        List.of(new ActivityInput(
                                null,
                                "Activity",
                                "Responsible",
                                "Instructions",
                                "1 day",
                                0,
                                List.of()
                        )),
                        List.of()
                ))
        );
    }
}
