package com.temaconsulting.agente_iso.verification.infrastructure.persistence;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.application.port.DriveEvidenceSearch;
import com.temaconsulting.agente_iso.integration.application.port.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.verification.application.DriveDocumentaryAssessmentService;
import com.temaconsulting.agente_iso.verification.application.port.TicketEvidenceAiPort;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JsonAccessDeniedHandler;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JsonAuthenticationEntryPoint;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JwtRoleConverter;
import com.temaconsulting.agente_iso.identity.infrastructure.security.SecurityConfig;
import com.temaconsulting.agente_iso.shared.api.error.ApiExceptionHandler;
import com.temaconsulting.agente_iso.shared.api.error.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.config.Configuration;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.api.error.VerificationApiExceptionHandler;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultConflictException;
import com.temaconsulting.agente_iso.verification.application.VerificationResultDecisionService;
import com.temaconsulting.agente_iso.verification.application.VerificationApplicabilityService;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultDecisionFailure;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultRecheck;
import com.temaconsulting.agente_iso.verification.application.model.AiRetryAttempt;
import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.port.AiRetryRepository;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultPort;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultProgress;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultQueryFailure;
import com.temaconsulting.agente_iso.verification.application.VerificationResultQueryService;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultValidationException;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.VerificationRunQueryService;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;
import com.temaconsulting.agente_iso.verification.application.VerificationRunWorker;
import com.temaconsulting.agente_iso.verification.application.VerificationActiveWork;
import com.temaconsulting.agente_iso.verification.application.ProgrammedEvidenceCheckService;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;
import com.temaconsulting.agente_iso.verification.domain.HumanDecisionType;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.sql.Timestamp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import({
        SecurityConfig.class,
        JwtRoleConverter.class,
        JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class,
        ApiExceptionHandler.class,
        VerificationApiExceptionHandler.class,
        ProblemDetailsFactory.class,
        Configuration.class
})
class JdbcVerificationResultRepositoryIntegrationTest {
    private static final Instant DECISION_TIME = Instant.parse("2026-10-14T22:00:00Z");

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
    VerificationResultPort results;

    @Autowired
    VerificationResultDecisionService resultDecisions;

    @Autowired
    VerificationApplicabilityService applicabilityService;

    @Autowired
    JdbcVerificationResultRepository resultRepository;

    @Autowired
    AiRetryRepository aiRetries;

    @Autowired
    VerificationActiveWork activeWork;

    @Test
    void exposesNotAttemptedActiveAndCompletedEvidenceIndividually() throws Exception {
        Fixture fixture = createFixture();
        UUID expectationId = fixture.documentExpectations().getFirst();
        var request = get("/api/v1/verifications/{runId}/work-items", fixture.runId())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mockMvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].searchStatus").value("NOT_ATTEMPTED"));
        activeWork.begin(fixture.runId(), expectationId, null);
        try {
            mockMvc.perform(get("/api/v1/verifications/{runId}/work-items", fixture.runId())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].searchStatus").value("IN_PROGRESS"));
        } finally {
            activeWork.clear(fixture.runId());
        }
        results.saveEvaluatedResult(command(fixture, expectationId, null,
                ResultStatus.DOES_NOT_COMPLY, ApplicabilityDecision.APPLIES,
                List.of(), List.of(), List.of()));
        mockMvc.perform(get("/api/v1/verifications/{runId}/work-items", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].searchStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$[1].searchStatus").value("NOT_ATTEMPTED"));
    }

    @Test
    void aiRetryKeepsHumanDecisionAndStoresRestrictedTicketSnapshot() throws Exception {
        Fixture fixture = createFixture();
        VerificationResult initial = results.saveEvaluatedResult(command(
                fixture, fixture.documentExpectations().getFirst(), null,
                ResultStatus.REVIEW_REQUIRED, ApplicabilityDecision.APPLIES,
                List.of(), List.of(), List.of()));
        completeRun(fixture.runId());
        VerificationResult human = resultDecisions.decide(fixture.runId(), initial.id(), 1,
                ResultStatus.COMPLIES, "Confirmado por Administrador.", fixture.userId());
        var attempt = new AiRetryAttempt(UUID.randomUUID(), fixture.runId(), initial.id(),
                fixture.userId(), DECISION_TIME, "[{\"id\":\"42\",\"subject\":\"Ticket observado\"}]",
                "{\"rationale\":\"Propuesta anterior\"}",
                human.revision(), human.proposedStatus(), human.effectiveStatus(),
                ResultStatus.DOES_NOT_COMPLY, human.effectiveStatus(), ApplicabilityDecision.APPLIES,
                "Nueva propuesta basada en Redmine.", "HUMAN_PRESERVED", "gemini-test", "response-1");
        VerificationResult updated = aiRetries.save(attempt,
                new ApplicabilityAssessment(ApplicabilityDecision.APPLIES,
                        DecisionOrigin.AI_APPLICABILITY, "Aplica según ticket.", List.of()),
                List.of("Requiere revisión de un campo."), List.of());
        assertThat(updated.proposedStatus()).isEqualTo(ResultStatus.DOES_NOT_COMPLY);
        assertThat(updated.effectiveStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(updated.humanDecisions()).hasSize(1);
        assertThat(aiRetries.list(fixture.runId(), initial.id())).singleElement()
                .satisfies(saved -> {
                    assertThat(saved.ticketSnapshotJson()).contains("Ticket observado");
                    assertThat(saved.previousResultJson()).contains("Propuesta anterior");
                    assertThat(saved.previousRevision()).isEqualTo(human.revision());
                });
        mockMvc.perform(get("/api/v1/verifications/{runId}/results/{resultId}/ai-retries",
                        fixture.runId(), initial.id())
                        .with(jwt().jwt(token -> token.subject(fixture.userId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketSnapshot[0].subject").value("Ticket observado"))
                .andExpect(jsonPath("$[0].previousResult.rationale").value("Propuesta anterior"));
        assertThrows(VerificationResultDecisionFailure.class, () ->
                aiRetries.save(attempt, updated.applicability(), List.of(), List.of()));
    }

    @Test
    void aiRetryAuditIsAdminOnlyAndGeminiMustBeConfigured() throws Exception {
        Fixture fixture = createFixture();
        VerificationResult result = results.saveEvaluatedResult(command(
                fixture, fixture.ticketExpectation(), "204",
                ResultStatus.REVIEW_REQUIRED, ApplicabilityDecision.APPLIES,
                List.of(), List.of(), List.of()));
        completeRun(fixture.runId());
        String retryPath = "/api/v1/verifications/{runId}/results/{resultId}/ai-retry";
        String historyPath = "/api/v1/verifications/{runId}/results/{resultId}/ai-retries";
        mockMvc.perform(post(retryPath, fixture.runId(), result.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(retryPath, fixture.runId(), result.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(retryPath, fixture.runId(), result.id())
                        .with(jwt().jwt(token -> token.subject(fixture.userId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get(historyPath, fixture.runId(), result.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(historyPath, fixture.runId(), result.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Autowired
    VerificationResultQueryService resultQueries;

    @Autowired
    VerificationRunRepository runs;

    @Autowired
    VerificationRunQueryService runQueries;

    @Autowired
    VerificationRunWorker worker;

    @MockitoBean
    ProgrammedEvidenceCheckService programmedChecks;

    @TestBean
    Clock customClock;

    @Autowired
    MockMvc mockMvc;

    static Clock customClock() {
        return Clock.fixed(DECISION_TIME, ZoneOffset.UTC);
    }

    @Test
    void publishedM1ConfigurationRunsWithFakeDriveRedmineAndAiAndPersistsHistory() throws Exception {
        Fixture fixture = createFixture();
        jdbc.update("""
                UPDATE services SET revision = 2,
                    drive_folder_id = 'folder-9', drive_folder_name = 'Servicio Drive',
                    redmine_project_id = 'project-9', redmine_project_name = 'Servicio Redmine'
                WHERE id = ?
                """, fixture.serviceId());
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO verification_runs (id, service_id, process_version_id, started_by,
                    service_revision_snapshot, scope, macro_activity_id, status, total)
                VALUES (?, ?, ?, ?, 2, 'MACRO_ACTIVITY'::verification_scope, ?, 'QUEUED'::run_status, 6)
                """, runId, fixture.serviceId(), fixture.processVersionId(), fixture.userId(),
                fixture.macroActivityId());
        jdbc.update("""
                INSERT INTO verification_ticket_selections (run_id, group_id, ticket_id)
                VALUES (?, ?, '204'), (?, ?, '205')
                """, runId, "ticket-group:" + fixture.macroActivityId(),
                runId, "ticket-group:" + fixture.macroActivityId());

        DriveEvidenceSearch drive = request -> DriveEvidenceSearch.SearchResult.found(
                List.of(new DriveEvidenceSearch.Match("file-9", "Fixture.pdf",
                        DriveEvidenceSearch.Kind.DOCUMENT, "https://drive.example/file-9", "Fixture.pdf")),
                DriveEvidenceSearch.Cardinality.ONE);
        RedmineTicketReader redmine = (projectId, ticketId) -> new TicketSnapshot(
                ticketId, projectId, "Servicio Redmine", "Entrega validada", "Closed", "5", "Closed",
                true, "Entrega revisada", null, DECISION_TIME, DECISION_TIME.minusSeconds(3600),
                List.of(new TicketSnapshot.CustomField("7", "Validación", "Aprobada")), List.of());
        TicketEvidenceAiPort ai = (name, canonical, nomenclature, policies, instructions, ticket) ->
                new TicketEvidenceAiPort.Assessment(ResultStatus.COMPLIES,
                        "El campo de validación indica aprobación.", List.of(),
                        List.of("customFields"), "gemini-test", "response-test");
        var realChecks = new ProgrammedEvidenceCheckService(
                new DriveDocumentaryAssessmentService(drive, customClock()), redmine, null, ai,
                customClock());
        when(programmedChecks.assess(any(), any(), any(), nullable(String.class), any(), any()))
                .thenAnswer(call -> realChecks.assess(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3), call.getArgument(4), call.getArgument(5)));

        worker.process(runId);
        String status = "QUEUED";
        for (int attempt = 0; attempt < 100; attempt++) {
            status = runQueries.get(runId).run().status();
            if (status.equals("COMPLETED") || status.equals("FAILED")) break;
            Thread.sleep(50);
        }
        assertThat(status).isEqualTo("COMPLETED");
        assertThat(runQueries.get(runId).progress().resultCount()).isEqualTo(6);
        assertThat(resultQueries.findCompletedRunResults(runId, 0, 20).items()).hasSize(6)
                .allSatisfy(result -> assertThat(result.proposedStatus()).isEqualTo(ResultStatus.COMPLIES));
        mockMvc.perform(get("/api/v1/verifications/{runId}", runId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceNameSnapshot").value("Result service"))
                .andExpect(jsonPath("$.processNameSnapshot").value("Start Up"))
                .andExpect(jsonPath("$.sourceSnapshot.driveFolderId").value("folder-9"));
    }




    @Test
    void asyncWorkerCompletesWithTheRealTc176ResultWriter() throws Exception {
        Fixture fixture = createFixture();
        jdbc.update("UPDATE verification_runs SET status = 'QUEUED'::run_status, total = 6 WHERE id = ?",
                fixture.runId());
        when(programmedChecks.assess(any(), any(), eq(ApplicabilityDecision.APPLIES), nullable(String.class), any(), any()))
                .thenAnswer(call -> {
                    var expectation = (com.temaconsulting.agente_iso.verification.domain.VerificationContext.ExpectationContext)
                            call.getArgument(0);
                    String ticketId = call.getArgument(3);
                    return new ProgrammedEvidenceCheckService.Assessment(expectation.expectationId(), ticketId,
                            ResultStatus.REVIEW_REQUIRED, "Revisión del registro", List.of(), List.of());
                });

        worker.process(fixture.runId());
        String state = "QUEUED";
        for (int attempt = 0; attempt < 100; attempt++) {
            state = runQueries.get(fixture.runId()).run().status();
            if (state.equals("COMPLETED") || state.equals("FAILED")) break;
            Thread.sleep(50);
        }
        assertThat(state).isEqualTo("COMPLETED");
        var finished = runQueries.get(fixture.runId());
        assertThat(finished.progress().resultCount()).isEqualTo(6);
        assertThat(finished.progress().reviewRequiredCount()).isEqualTo(6);
        assertThat(finished.run().finishedAt()).isNotNull();
        assertThat(resultQueries.findCompletedRunResults(fixture.runId(), 0, 20).items()).hasSize(6);
    }

    @Test
    void runProgressUsesPersistedResultsAndCompletesOnlyAtTotal() throws Exception {
        Fixture fixture = createFixture();
        jdbc.update("UPDATE verification_runs SET total = 6 WHERE id = ?", fixture.runId());
        results.saveEvaluatedResult(command(fixture, fixture.documentExpectations().getFirst(), null,
                ResultStatus.REVIEW_REQUIRED, ApplicabilityDecision.UNDETERMINED,
                List.of("No hay datos suficientes"), List.of(), List.of()));

        assertThat(runs.markCompleted(fixture.runId())).isFalse();
        var partial = runQueries.get(fixture.runId());
        assertThat(partial.run().status()).isEqualTo("RUNNING");
        assertThat(partial.run().total()).isEqualTo(6);
        assertThat(partial.progress().resultCount()).isEqualTo(1);
        assertThat(partial.progress().reviewRequiredCount()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/verifications/{runId}", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.progress.total").value(6))
                .andExpect(jsonPath("$.progress.processed").value(1));

        for (int index = 1; index < fixture.documentExpectations().size(); index++) {
            results.saveEvaluatedResult(command(fixture, fixture.documentExpectations().get(index), null,
                    ResultStatus.COMPLIES, ApplicabilityDecision.APPLIES, List.of(), List.of(), List.of()));
        }
        for (String ticketId : List.of("204", "205")) {
            results.saveEvaluatedResult(command(fixture, fixture.ticketExpectation(), ticketId,
                    ResultStatus.REVIEW_REQUIRED, ApplicabilityDecision.APPLIES, List.of(), List.of(), List.of()));
        }
        assertThat(runs.markCompleted(fixture.runId())).isTrue();
        var completed = runQueries.get(fixture.runId());
        assertThat(completed.run().status()).isEqualTo("COMPLETED");
        assertThat(completed.run().finishedAt()).isNotNull();
        assertThat(completed.progress().resultCount()).isEqualTo(6);
        assertThat(completed.progress().reviewRequiredCount()).isEqualTo(3);
    }

    @Test
    void historyPagesAreStableFilteredAndKeepRunSnapshotsAndSelections() throws Exception {
        Fixture fixture = createFixture();
        String originalServiceName = jdbc.queryForObject("SELECT name FROM services WHERE id = ?",
                String.class, fixture.serviceId());
        String originalProcessName = jdbc.queryForObject("""
                SELECT p.name FROM processes p JOIN process_versions pv ON pv.process_id = p.id
                WHERE pv.id = ?
                """, String.class, fixture.processVersionId());
        jdbc.update("""
                UPDATE services
                SET revision = 2, service_type = 'SOFTWARE_FACTORY'::service_type,
                    drive_folder_id = 'drive-at-start', drive_folder_name = 'Drive at start',
                    redmine_project_id = 'redmine-at-start', redmine_project_name = 'Redmine at start'
                WHERE id = ?
                """, fixture.serviceId());

        Instant sharedStartedAt = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        Timestamp startedAt = Timestamp.from(sharedStartedAt);
        List<UUID> insertedRunIds = new ArrayList<>();
        List<String> statuses = List.of("QUEUED", "RUNNING", "COMPLETED", "FAILED");
        for (int index = 0; index < 23; index++) {
            UUID runId = UUID.randomUUID();
            String runStatus = statuses.get(index % statuses.size());
            insertedRunIds.add(runId);
            jdbc.update("""
                    INSERT INTO verification_runs (
                        id, service_id, process_version_id, started_by, service_revision_snapshot,
                        scope, macro_activity_id, note, status, started_at, finished_at,
                        failure_message, total
                    ) VALUES (?, ?, ?, ?, 2, 'MACRO_ACTIVITY'::verification_scope, ?, ?, ?::run_status,
                              ?, ?, ?, 4)
                    """,
                    runId,
                    fixture.serviceId(),
                    fixture.processVersionId(),
                    fixture.userId(),
                    fixture.macroActivityId(),
                    "run note " + index,
                    runStatus,
                    startedAt,
                    runStatus.equals("COMPLETED") || runStatus.equals("FAILED")
                            ? Timestamp.from(sharedStartedAt.plusSeconds(10)) : null,
                    runStatus.equals("FAILED") ? "Safe failure message" : null
            );
            if (index == 0) {
                jdbc.update("""
                        INSERT INTO verification_ticket_selections (run_id, group_id, ticket_id)
                        VALUES (?, ?, '301'), (?, ?, '302')
                        """,
                        runId,
                        "ticket-group:" + fixture.macroActivityId(),
                        runId,
                        "ticket-group:" + fixture.macroActivityId()
                );
            }
        }
        jdbc.update("""
                UPDATE services
                SET revision = 3, name = 'Renamed service', service_type = 'PROJECT_ERP'::service_type,
                    drive_folder_id = 'drive-current', drive_folder_name = 'Current Drive',
                    redmine_project_id = 'redmine-current', redmine_project_name = 'Current Redmine'
                WHERE id = ?
                """, fixture.serviceId());

        var firstPage = runs.list(fixture.serviceId(), 0, 20);
        var secondPage = runs.list(fixture.serviceId(), 1, 20);
        List<UUID> actualIds = new ArrayList<>(firstPage.items().stream()
                .map(run -> run.id()).toList());
        actualIds.addAll(secondPage.items().stream().map(run -> run.id()).toList());
        List<VerificationRunSnapshot> allRuns = new ArrayList<>(firstPage.items());
        allRuns.addAll(secondPage.items());
        List<UUID> expectedIds = jdbc.query(
                """
                SELECT id FROM verification_runs
                WHERE service_id = ?
                ORDER BY started_at DESC, id DESC
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                fixture.serviceId()
        );

        assertThat(firstPage.totalElements()).isEqualTo(24);
        assertThat(firstPage.items()).hasSize(20);
        assertThat(secondPage.items()).hasSize(4);
        assertThat(actualIds).containsExactlyElementsOf(expectedIds);
        assertThat(allRuns.stream().map(VerificationRunSnapshot::status))
                .contains("QUEUED", "RUNNING", "COMPLETED", "FAILED");
        assertThat(runs.list(UUID.randomUUID(), 0, 20).items()).isEmpty();
        assertThat(runs.list(UUID.randomUUID(), 0, 20).totalElements()).isZero();
        var unfilteredIds = runs.list(null, 0, 100).items().stream()
                .map(VerificationRunSnapshot::id)
                .toList();
        assertThat(unfilteredIds).containsAll(insertedRunIds).contains(fixture.runId());

        var snapshot = allRuns.stream()
                .filter(run -> run.id().equals(insertedRunIds.getFirst()))
                .findFirst().orElseThrow();
        assertThat(snapshot.serviceTypeSnapshot()).isEqualTo(ServiceType.SOFTWARE_FACTORY);
        assertThat(snapshot.serviceRevisionSnapshot()).isEqualTo(2);
        assertThat(snapshot.serviceNameSnapshot()).isEqualTo(originalServiceName);
        assertThat(snapshot.processNameSnapshot()).isEqualTo(originalProcessName);
        assertThat(snapshot.processOfficialVersionSnapshot()).isNotBlank();
        assertThat(snapshot.macroActivityNameSnapshot()).isNotBlank();
        assertThat(snapshot.processVersionId()).isEqualTo(fixture.processVersionId());
        assertThat(snapshot.driveFolderId()).isEqualTo("drive-at-start");
        assertThat(snapshot.driveFolderName()).isEqualTo("Drive at start");
        assertThat(snapshot.redmineProjectId()).isEqualTo("redmine-at-start");
        assertThat(snapshot.redmineProjectName()).isEqualTo("Redmine at start");
        assertThat(snapshot.scope()).isEqualTo(VerificationScope.MACRO_ACTIVITY);
        assertThat(snapshot.macroActivityId()).isEqualTo(fixture.macroActivityId());
        assertThat(snapshot.startedBy()).isEqualTo(fixture.userId());
        assertThat(snapshot.startedAt()).isEqualTo(sharedStartedAt);
        assertThat(snapshot.note()).isEqualTo("run note 0");
        assertThat(snapshot.total()).isEqualTo(4);
        assertThat(snapshot.status()).isEqualTo("QUEUED");
        assertThat(snapshot.ticketSelections().groups()).hasSize(1);
        assertThat(snapshot.ticketSelections().groups().getFirst().ticketIds())
                .containsExactly("301", "302");
        var failedRun = firstPage.items().stream()
                .filter(run -> run.status().equals("FAILED"))
                .findFirst().orElseThrow();
        assertThat(failedRun.failureMessage()).isEqualTo("Safe failure message");
        var noSelections = allRuns.stream()
                .filter(run -> run.id().equals(insertedRunIds.get(1)))
                .findFirst().orElseThrow();
        assertThat(noSelections.ticketSelections().groups()).isEmpty();

        mockMvc.perform(get("/api/v1/verifications")
                        .param("serviceId", fixture.serviceId().toString())
                        .param("page", "3")
                        .param("size", "20")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.page").value(3))
                .andExpect(jsonPath("$.page.totalElements").value(24))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        mockMvc.perform(get("/api/v1/verifications")
                        .param("size", "101")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/verifications")
                        .param("page", "-1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void historyProgressUsesCurrentEffectiveResultStatus() {
        Fixture fixture = createFixture();
        jdbc.update("UPDATE verification_runs SET total = 1 WHERE id = ?", fixture.runId());
        VerificationResult result = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().getFirst(),
                null,
                ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));

        var beforeDecision = runQueries.list(fixture.serviceId(), 0, 20).items().getFirst();
        assertThat(beforeDecision.progress()).isEqualTo(new VerificationResultProgress(1, 1));
        completeRun(fixture.runId());
        resultDecisions.decide(
                fixture.runId(), result.id(), 1, ResultStatus.COMPLIES, "Revisión humana completada.",
                fixture.userId()
        );

        var afterDecision = runQueries.list(fixture.serviceId(), 0, 20).items().getFirst();
        assertThat(afterDecision.run().total()).isEqualTo(1);
        assertThat(afterDecision.progress()).isEqualTo(new VerificationResultProgress(1, 0));
    }

    @Test
    void startupRecoveryFailsInterruptedRunAndKeepsPartialResults() {
        Fixture fixture = createFixture();
        jdbc.update("UPDATE verification_runs SET total = 6 WHERE id = ?", fixture.runId());
        results.saveEvaluatedResult(command(fixture, fixture.documentExpectations().getFirst(), null,
                ResultStatus.COMPLIES, ApplicabilityDecision.APPLIES, List.of(), List.of(), List.of()));
        assertThat(runs.failInterruptedRuns()).isGreaterThanOrEqualTo(1);
        var failed = runQueries.get(fixture.runId());
        assertThat(failed.run().status()).isEqualTo("FAILED");
        assertThat(failed.run().finishedAt()).isNotNull();
        assertThat(failed.progress().resultCount()).isEqualTo(1);
    }

    @Test
    void persistsAllResultStatesAndLoadsTheirCompleteIndependentEvaluations() {
        Fixture fixture = createFixture();
        Instant observedAt = Instant.parse("2026-10-13T12:00:00Z");
        var driveReferences = List.of(
                new VerificationResultWriteCommand.EvidenceReference(
                        EvidenceType.DOCUMENT,
                        com.temaconsulting.agente_iso.integration.domain.IntegrationSource.DRIVE,
                        "drive-doc-1",
                        "Planilla de control.xlsx",
                        "https://drive.google.com/file/d/drive-doc-1",
                        observedAt,
                        null
                ),
                new VerificationResultWriteCommand.EvidenceReference(
                        EvidenceType.DOCUMENT,
                        com.temaconsulting.agente_iso.integration.domain.IntegrationSource.DRIVE,
                        "drive-doc-2",
                        "Planilla de control - copia.xlsx",
                        null,
                        observedAt.plusSeconds(1),
                        null
                )
        );

        VerificationResultWriteCommand document = command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of("La expectativa aplica por el tipo de servicio"),
                List.of("Solo se verificó existencia", "No se evaluó el contenido"),
                driveReferences
        );
        VerificationResult first = results.saveEvaluatedResult(document);
        results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(1),
                null,
                ResultStatus.DOES_NOT_COMPLY,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(2),
                null,
                ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of("La fuente no permitió completar la búsqueda"),
                List.of()
        ));
        results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(3),
                null,
                ResultStatus.NOT_APPLICABLE,
                ApplicabilityDecision.NOT_APPLICABLE,
                List.of("No se requiere para este caso"),
                List.of(),
                List.of()
        ));
        VerificationResult ticket204 = results.saveEvaluatedResult(command(
                fixture,
                fixture.ticketExpectation(),
                "204",
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        VerificationResult ticket205 = results.saveEvaluatedResult(command(
                fixture,
                fixture.ticketExpectation(),
                "205",
                ResultStatus.DOES_NOT_COMPLY,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(ticket204.id()).isNotEqualTo(ticket205.id());
        assertThat(ticket204.subjectTicketId()).isEqualTo("204");
        assertThat(ticket205.subjectTicketId()).isEqualTo("205");
        assertThat(ticket204.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(ticket205.proposedStatus()).isEqualTo(ResultStatus.DOES_NOT_COMPLY);

        var repeated = results.saveEvaluatedResult(document);
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM verification_result_evidence WHERE result_id = ?",
                Long.class,
                first.id()
        )).isEqualTo(2);
        assertThat(results.progress(fixture.runId())).isEqualTo(new VerificationResultProgress(6, 1));

        var changed = command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.DOES_NOT_COMPLY,
                ApplicabilityDecision.APPLIES,
                List.of("La expectativa aplica por el tipo de servicio"),
                List.of("Solo se verificó existencia", "No se evaluó el contenido"),
                driveReferences
        );
        assertThrows(VerificationResultConflictException.class, () -> results.saveEvaluatedResult(changed));

        List<VerificationResult> stored = resultsForCompletedFixture(fixture);
        assertThat(stored).hasSize(6);
        VerificationResult storedDocument = stored.stream()
                .filter(result -> result.id().equals(first.id()))
                .findFirst()
                .orElseThrow();
        assertThat(storedDocument.displayName()).isEqualTo("Document expectation 1");
        assertThat(storedDocument.evidenceType()).isEqualTo(EvidenceType.DOCUMENT);
        assertThat(storedDocument.location()).isEqualTo(
                com.temaconsulting.agente_iso.process.domain.EvidenceLocation.INPUT
        );
        assertThat(storedDocument.activityId()).isNull();
        assertThat(storedDocument.applicability().limitations())
                .containsExactly("La expectativa aplica por el tipo de servicio");
        assertThat(storedDocument.applicability().decision()).isEqualTo(ApplicabilityDecision.APPLIES);
        assertThat(storedDocument.applicability().origin()).isEqualTo(DecisionOrigin.DETERMINISTIC);
        assertThat(storedDocument.applicability().rationale())
                .isEqualTo("Applicability assessment rationale");
        assertThat(storedDocument.limitations())
                .containsExactly("Solo se verificó existencia", "No se evaluó el contenido");
        assertThat(storedDocument.evidence()).hasSize(2);
        assertThat(storedDocument.evidence().getFirst().externalId()).isEqualTo("drive-doc-1");
        assertThat(storedDocument.evidence().getFirst().source())
                .isEqualTo(com.temaconsulting.agente_iso.integration.domain.IntegrationSource.DRIVE);
        assertThat(storedDocument.evidence().getFirst().webUrl())
                .isEqualTo("https://drive.google.com/file/d/drive-doc-1");
        assertThat(storedDocument.evidence().getFirst().observedAt())
                .isEqualTo(Instant.parse("2026-10-13T12:00:00Z"));
        assertThat(storedDocument.proposedStatus()).isEqualTo(storedDocument.effectiveStatus());
        assertThat(storedDocument.humanDecisions()).isEmpty();
        assertThat(storedDocument.revision()).isEqualTo(1);
        assertThat(stored.stream().filter(result -> result.expectationId()
                .equals(fixture.documentExpectations().get(0))).count()).isEqualTo(1);
        assertThat(stored.stream().filter(result -> result.expectationId()
                .equals(fixture.documentExpectations().get(1))).count()).isEqualTo(1);
        assertThat(stored.stream().filter(result -> result.expectationId()
                .equals(fixture.documentExpectations().get(3))).findFirst().orElseThrow().activityId())
                .isEqualTo(fixture.activityId());
        assertThat(stored).extracting(VerificationResult::proposedStatus)
                .contains(
                        ResultStatus.COMPLIES,
                        ResultStatus.DOES_NOT_COMPLY,
                        ResultStatus.REVIEW_REQUIRED,
                        ResultStatus.NOT_APPLICABLE
                );
    }

    @Test
    void rejectsInvalidTicketSubjectsAndRollsBackWhenAnEvidenceReferenceCannotBeWritten() {
        Fixture fixture = createFixture();
        assertThrows(
                VerificationResultValidationException.class,
                () -> results.saveEvaluatedResult(command(
                        fixture,
                        fixture.ticketExpectation(),
                        "999",
                        ResultStatus.COMPLIES,
                        ApplicabilityDecision.APPLIES,
                        List.of(),
                        List.of(),
                        List.of()
                ))
        );
        assertThrows(
                VerificationResultValidationException.class,
                () -> results.saveEvaluatedResult(command(
                        fixture,
                        fixture.documentExpectations().get(0),
                        "204",
                        ResultStatus.COMPLIES,
                        ApplicabilityDecision.APPLIES,
                        List.of(),
                        List.of(),
                        List.of()
                ))
        );
        assertThrows(
                VerificationResultValidationException.class,
                () -> results.saveEvaluatedResult(new VerificationResultWriteCommand(
                        fixture.runId(),
                        fixture.outOfScopeExpectation(),
                        fixture.otherMacroActivityId(),
                        null,
                        "Out-of-scope expectation",
                        null,
                        ApplicabilityDecision.APPLIES,
                        DecisionOrigin.DETERMINISTIC,
                        "Applicability assessment rationale",
                        List.of(),
                        ResultStatus.COMPLIES,
                        "Evaluation rationale",
                        List.of(),
                        List.of()
                ))
        );

        var invalidReference = new VerificationResultWriteCommand.EvidenceReference(
                EvidenceType.DOCUMENT,
                com.temaconsulting.agente_iso.integration.domain.IntegrationSource.DRIVE,
                "x".repeat(256),
                "Drive document",
                null,
                Instant.parse("2026-10-13T12:00:00Z"),
                null
        );
        assertThrows(DataIntegrityViolationException.class, () -> results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of("Applicability limitation"),
                List.of("Result limitation"),
                List.of(invalidReference)
        )));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM verification_results WHERE run_id = ?",
                Long.class,
                fixture.runId()
        )).isZero();
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM verification_result_applicability_limitations al
                JOIN verification_results vr ON vr.id = al.result_id
                WHERE vr.run_id = ?
                """,
                Long.class,
                fixture.runId()
        )).isZero();
    }

    @Test
    void endpointPaginatesStablyReturnsEmptyArraysAndEnforcesRunStateAndPermissions() throws Exception {
        Fixture fixture = createFixture();
        for (UUID expectationId : fixture.documentExpectations()) {
            List<VerificationResultWriteCommand.EvidenceReference> evidence =
                    expectationId.equals(fixture.documentExpectations().getFirst())
                            ? List.of(new VerificationResultWriteCommand.EvidenceReference(
                                    EvidenceType.DOCUMENT,
                                    com.temaconsulting.agente_iso.integration.domain.IntegrationSource.DRIVE,
                                    "drive-api-result",
                                    "API result reference",
                                    "https://drive.google.com/file/d/api-result",
                                    Instant.parse("2026-10-13T12:00:00Z"),
                                    null
                            ))
                            : List.of();
            results.saveEvaluatedResult(command(
                    fixture,
                    expectationId,
                    null,
                    ResultStatus.COMPLIES,
                    ApplicabilityDecision.APPLIES,
                    List.of(),
                    List.of(),
                    evidence
            ));
        }
        results.saveEvaluatedResult(command(
                fixture,
                fixture.ticketExpectation(),
                "204",
                ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        results.saveEvaluatedResult(command(
                fixture,
                fixture.ticketExpectation(),
                "205",
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));

        mockMvc.perform(get("/api/v1/verifications/{runId}/results", fixture.runId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/verifications/{runId}/results", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER")))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        completeRun(fixture.runId());
        mockMvc.perform(get("/api/v1/verifications/{runId}/results", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].expectationId").value(
                        fixture.documentExpectations().get(0).toString()))
                .andExpect(jsonPath("$.items[0].evidence[0].kind").value("DOCUMENT"))
                .andExpect(jsonPath("$.items[0].evidence[0].source").value("DRIVE"))
                .andExpect(jsonPath("$.items[0].evidence[0].externalId").value("drive-api-result"))
                .andExpect(jsonPath("$.items[0].evidence[0].webUrl")
                        .value("https://drive.google.com/file/d/api-result"))
                .andExpect(jsonPath("$.items[0].subjectTicketId").doesNotExist())
                .andExpect(jsonPath("$.items[1].evidence").isArray())
                .andExpect(jsonPath("$.items[1].evidence.length()").value(0))
                .andExpect(jsonPath("$.items[1].limitations").isArray())
                .andExpect(jsonPath("$.items[1].limitations.length()").value(0))
                .andExpect(jsonPath("$.items[1].applicability.limitations.length()").value(0))
                .andExpect(jsonPath("$.items[1].humanDecisions").isArray())
                .andExpect(jsonPath("$.items[1].humanDecisions.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(6))
                .andExpect(jsonPath("$.page.totalPages").value(3));
        mockMvc.perform(get("/api/v1/verifications/{runId}/results", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER")))
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].subjectTicketId").value("204"))
                .andExpect(jsonPath("$.items[1].subjectTicketId").value("205"));
        mockMvc.perform(get("/api/v1/verifications/{runId}/results", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/v1/verifications/{runId}/results", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        jdbc.update("UPDATE verification_runs SET status = 'QUEUED'::run_status WHERE id = ?", fixture.runId());
        mockMvc.perform(get("/api/v1/verifications/{runId}/results", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE verification_runs SET status = 'FAILED'::run_status WHERE id = ?", fixture.runId());
        mockMvc.perform(get("/api/v1/verifications/{runId}/results", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict());

        Fixture emptyFixture = createFixture();
        completeRun(emptyFixture.runId());
        mockMvc.perform(get("/api/v1/verifications/{runId}/results", emptyFixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0));
    }

    @Test
    void readsHumanDecisionEventsFromOldestToNewestAndProgressUsesEffectiveStatus() throws Exception {
        Fixture fixture = createFixture(true);
        VerificationResult saved = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.UNDETERMINED,
                List.of(),
                List.of(),
                List.of()
        ));
        Instant older = Instant.parse("2026-10-13T10:00:00Z");
        Instant newer = older.plusSeconds(60);
        UUID applicabilityEventId = UUID.randomUUID();
        UUID statusEventId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO human_decisions (
                    id, result_id, decided_by, decision_type, applicability_value,
                    rationale, decided_at
                ) VALUES (?, ?, ?, 'APPLICABILITY'::human_decision_type,
                          'APPLIES'::applicability_decision, 'Confirmed applicability', ?)
                """,
                applicabilityEventId,
                saved.id(),
                fixture.userId(),
                java.sql.Timestamp.from(older)
        );
        jdbc.update(
                """
                INSERT INTO human_decisions (
                    id, result_id, decided_by, decision_type, status_value, rationale, decided_at
                ) VALUES (?, ?, ?, 'RESULT_STATUS'::human_decision_type,
                          'COMPLIES'::result_status, 'Confirmed evidence', ?)
                """,
                statusEventId,
                saved.id(),
                fixture.userId(),
                java.sql.Timestamp.from(newer)
        );
        jdbc.update(
                "UPDATE verification_results SET effective_status = 'COMPLIES'::result_status, revision = 3 WHERE id = ?",
                saved.id()
        );

        assertThat(results.progress(fixture.runId())).isEqualTo(new VerificationResultProgress(1, 0));
        assertThrows(
                VerificationResultQueryFailure.class,
                () -> results.progress(UUID.randomUUID())
        );
        completeRun(fixture.runId());
        List<VerificationResult> stored = resultsForCompletedFixture(fixture);
        VerificationResult reloaded = stored.getFirst();
        assertThat(reloaded.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(reloaded.effectiveStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(reloaded.applicability().origin()).isEqualTo(DecisionOrigin.HUMAN);
        assertThat(reloaded.revision()).isEqualTo(3);
        assertThat(reloaded.humanDecisions()).extracting("id")
                .containsExactly(applicabilityEventId, statusEventId);
        assertThat(reloaded.humanDecisions().getFirst().decisionType())
                .isEqualTo(HumanDecisionType.APPLICABILITY);
        mockMvc.perform(get("/api/v1/verifications/{runId}/results", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].humanDecisions[0].applicabilityDecision").value("APPLIES"))
                .andExpect(jsonPath("$.items[0].humanDecisions[0].finalStatus").doesNotExist())
                .andExpect(jsonPath("$.items[0].humanDecisions[1].finalStatus").value("COMPLIES"))
                .andExpect(jsonPath("$.items[0].humanDecisions[1].applicabilityDecision").doesNotExist());
    }

    @Test
    void confirmsAndCorrectsResultWithoutChangingTheSystemProposal() throws Exception {
        Fixture fixture = createFixture();
        VerificationResult saved = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        jdbc.update("UPDATE verification_runs SET total = 1 WHERE id = ?", fixture.runId());
        completeRun(fixture.runId());
        var before = runQueries.get(fixture.runId());

        String body = """
                {"revision":1,"finalStatus":"COMPLIES",
                 "rationale":"Evidencia verificada por el administrador.",
                 "decidedBy":"%s"}
                """.formatted(UUID.randomUUID());
        mockMvc.perform(decisionRequest(fixture.runId(), saved.id(), fixture.userId(), "ADMIN", body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.effectiveStatus").value("COMPLIES"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.rationale").value("Evaluation rationale for REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.humanDecisions.length()").value(1))
                .andExpect(jsonPath("$.humanDecisions[0].decisionType").value("RESULT_STATUS"))
                .andExpect(jsonPath("$.humanDecisions[0].finalStatus").value("COMPLIES"))
                .andExpect(jsonPath("$.humanDecisions[0].decidedBy").value(fixture.userId().toString()))
                .andExpect(jsonPath("$.humanDecisions[0].decidedAt").value(DECISION_TIME.toString()));
        mockMvc.perform(decisionRequest(
                        fixture.runId(), saved.id(), fixture.userId(), "ADMIN",
                        decisionBody(2, "DOES_NOT_COMPLY", "La evidencia no es válida.")
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.effectiveStatus").value("DOES_NOT_COMPLY"))
                .andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.humanDecisions.length()").value(2))
                .andExpect(jsonPath("$.humanDecisions[0].finalStatus").value("COMPLIES"))
                .andExpect(jsonPath("$.humanDecisions[1].finalStatus").value("DOES_NOT_COMPLY"));

        var after = runQueries.get(fixture.runId());
        assertThat(after.run().total()).isEqualTo(before.run().total());
        assertThat(after.progress().resultCount()).isEqualTo(before.progress().resultCount());
        assertThat(before.progress().reviewRequiredCount()).isEqualTo(1);
        assertThat(after.progress().reviewRequiredCount()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM human_decisions WHERE result_id = ?",
                Long.class,
                saved.id()
        )).isEqualTo(2);
        verifyNoInteractions(programmedChecks);
    }

    @Test
    void confirmationWithTheSameEffectiveStateStillAppendsAnEvent() throws Exception {
        Fixture fixture = createFixture();
        VerificationResult saved = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        completeRun(fixture.runId());

        mockMvc.perform(decisionRequest(
                        fixture.runId(), saved.id(), fixture.userId(), "ADMIN",
                        decisionBody(1, "REVIEW_REQUIRED", "Confirmo el estado propuesto.")
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.effectiveStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.humanDecisions.length()").value(1));
    }

    @Test
    void limitsStatusByEffectiveApplicabilityAndExpectationMode() throws Exception {
        Fixture fixture = createFixture(false, true);
        VerificationResult undetermined = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.UNDETERMINED,
                List.of(),
                List.of(),
                List.of()
        ));
        VerificationResult applies = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(1),
                null,
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        VerificationResult notApplicable = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(2),
                null,
                ResultStatus.NOT_APPLICABLE,
                ApplicabilityDecision.NOT_APPLICABLE,
                List.of(),
                List.of(),
                List.of()
        ));
        VerificationResult always = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(3),
                null,
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        completeRun(fixture.runId());

        mockMvc.perform(decisionRequest(
                        fixture.runId(), undetermined.id(), fixture.userId(), "ADMIN",
                        decisionBody(1, "COMPLIES", "No se puede decidir aplicabilidad.")
                ))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(decisionRequest(
                        fixture.runId(), applies.id(), fixture.userId(), "ADMIN",
                        decisionBody(1, "NOT_APPLICABLE", "La expectativa no aplica.")
                ))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(decisionRequest(
                        fixture.runId(), always.id(), fixture.userId(), "ADMIN",
                        decisionBody(1, "NOT_APPLICABLE", "La expectativa no aplica.")
                ))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(decisionRequest(
                        fixture.runId(), notApplicable.id(), fixture.userId(), "ADMIN",
                        decisionBody(1, "NOT_APPLICABLE", "No corresponde para este caso.")
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveStatus").value("NOT_APPLICABLE"));
    }

    @Test
    void returnsExpectedAuthorizationNotFoundConflictAndInputErrors() throws Exception {
        Fixture fixture = createFixture();
        VerificationResult saved = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        completeRun(fixture.runId());
        String validBody = decisionBody(1, "COMPLIES", "Decisión válida.");

        mockMvc.perform(put("/api/v1/verifications/{runId}/results/{resultId}/decision",
                        fixture.runId(), saved.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(decisionRequest(fixture.runId(), saved.id(), fixture.userId(), "READER", validBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(decisionRequest(
                        fixture.runId(), UUID.randomUUID(), fixture.userId(), "ADMIN", validBody
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(decisionRequest(
                        UUID.randomUUID(), saved.id(), fixture.userId(), "ADMIN", validBody
                ))
                .andExpect(status().isNotFound());

        Fixture secondFixture = createFixture();
        VerificationResult foreignResult = results.saveEvaluatedResult(command(
                secondFixture,
                secondFixture.documentExpectations().get(0),
                null,
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        completeRun(secondFixture.runId());
        mockMvc.perform(decisionRequest(
                        fixture.runId(), foreignResult.id(), fixture.userId(), "ADMIN", validBody
                ))
                .andExpect(status().isNotFound());

        Fixture runningFixture = createFixture();
        VerificationResult runningResult = results.saveEvaluatedResult(command(
                runningFixture,
                runningFixture.documentExpectations().get(0),
                null,
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        mockMvc.perform(decisionRequest(
                        runningFixture.runId(), runningResult.id(), runningFixture.userId(), "ADMIN",
                        decisionBody(1, "COMPLIES", "Todavía no terminó.")
                ))
                .andExpect(status().isConflict());

        mockMvc.perform(decisionRequest(
                        fixture.runId(), saved.id(), fixture.userId(), "ADMIN", validBody
                ))
                .andExpect(status().isOk());
        mockMvc.perform(decisionRequest(
                        fixture.runId(), saved.id(), fixture.userId(), "ADMIN", validBody
                ))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM human_decisions WHERE result_id = ?",
                Long.class,
                saved.id()
        )).isEqualTo(1);

        for (String invalidBody : List.of(
                "{",
                "{\"finalStatus\":\"COMPLIES\",\"rationale\":\"falta revisión\"}",
                "{\"revision\":1,\"finalStatus\":\"UNKNOWN\",\"rationale\":\"estado desconocido\"}",
                "{\"revision\":1,\"finalStatus\":\"COMPLIES\",\"rationale\":\"\"}",
                "{\"revision\":1,\"finalStatus\":\"COMPLIES\",\"rationale\":\"   \"}"
        )) {
            mockMvc.perform(decisionRequest(
                            fixture.runId(), saved.id(), fixture.userId(), "ADMIN", invalidBody
                    ))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void decisionIsolatedToOneTicketAndOneRun() throws Exception {
        Fixture fixture = createFixture();
        VerificationResult ticket204 = results.saveEvaluatedResult(command(
                fixture, fixture.ticketExpectation(), "204", ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.APPLIES, List.of(), List.of(), List.of()
        ));
        VerificationResult ticket205 = results.saveEvaluatedResult(command(
                fixture, fixture.ticketExpectation(), "205", ResultStatus.DOES_NOT_COMPLY,
                ApplicabilityDecision.APPLIES, List.of(), List.of(), List.of()
        ));
        Fixture anotherRun = createFixture();
        VerificationResult otherRunTicket = results.saveEvaluatedResult(command(
                anotherRun, anotherRun.ticketExpectation(), "204", ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES, List.of(), List.of(), List.of()
        ));
        completeRun(fixture.runId());
        completeRun(anotherRun.runId());

        mockMvc.perform(decisionRequest(
                        fixture.runId(), ticket204.id(), fixture.userId(), "ADMIN",
                        decisionBody(1, "COMPLIES", "Ticket 204 corregido.")
                ))
                .andExpect(status().isOk());

        assertThat(resultsForCompletedFixture(fixture))
                .filteredOn(result -> result.id().equals(ticket205.id()))
                .singleElement()
                .extracting(VerificationResult::effectiveStatus)
                .isEqualTo(ResultStatus.DOES_NOT_COMPLY);
        assertThat(resultsForCompletedFixture(anotherRun))
                .filteredOn(result -> result.id().equals(otherRunTicket.id()))
                .singleElement()
                .extracting(VerificationResult::effectiveStatus)
                .isEqualTo(ResultStatus.COMPLIES);
    }

    @Test
    void rollsBackDecisionEventWhenUpdatingTheResultFails() {
        Fixture fixture = createFixture();
        VerificationResult saved = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        completeRun(fixture.runId());
        jdbc.execute("""
                CREATE FUNCTION reject_test_result_decision_update() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced decision write failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER trg_test_result_decision_update
                BEFORE UPDATE OF effective_status ON verification_results
                FOR EACH ROW EXECUTE FUNCTION reject_test_result_decision_update()
                """);
        try {
            assertThrows(
                    org.springframework.dao.DataAccessException.class,
                    () -> resultDecisions.decide(
                            fixture.runId(),
                            saved.id(),
                            1,
                            ResultStatus.COMPLIES,
                            "Debe revertirse todo.",
                            fixture.userId()
                    )
            );
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_test_result_decision_update ON verification_results");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_test_result_decision_update()");
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM human_decisions WHERE result_id = ?",
                Long.class,
                saved.id()
        )).isZero();
        assertThat(jdbc.queryForMap(
                "SELECT effective_status::text AS status, revision FROM verification_results WHERE id = ?",
                saved.id()
        )).containsEntry("status", "REVIEW_REQUIRED").containsEntry("revision", 1);
    }

    @Test
    void concurrentDecisionsWithSameRevisionOnlyAppendOneEvent() throws Exception {
        Fixture fixture = createFixture();
        VerificationResult saved = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        completeRun(fixture.runId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<MvcResult> request = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent requests did not start");
                }
                return mockMvc.perform(decisionRequest(
                        fixture.runId(), saved.id(), fixture.userId(), "ADMIN",
                        decisionBody(1, "COMPLIES", "Decisión concurrente.")
                )).andReturn();
            };
            Future<MvcResult> first = executor.submit(request);
            Future<MvcResult> second = executor.submit(request);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(10, TimeUnit.SECONDS).getResponse().getStatus()
            )).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM human_decisions WHERE result_id = ?",
                Long.class,
                saved.id()
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT revision FROM verification_results WHERE id = ?",
                Integer.class,
                saved.id()
        )).isEqualTo(2);
    }

    @Test
    void resolvesNotApplicableWithoutSourceCallsOrChangingTheAutomaticProposal() throws Exception {
        Fixture fixture = createFixture(true);
        VerificationResult saved = results.saveEvaluatedResult(command(fixture,
                fixture.documentExpectations().getFirst(), null, ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.UNDETERMINED, List.of("Sin hechos"), List.of("Pendiente"), List.of()));
        completeRun(fixture.runId());

        mockMvc.perform(applicabilityRequest(fixture.runId(), saved.id(), fixture.userId(), "ADMIN",
                applicabilityBody(1, "NOT_APPLICABLE", "No hubo desarrollo.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.id().toString()))
                .andExpect(jsonPath("$.proposedStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.effectiveStatus").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.applicability.decision").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.applicability.origin").value("HUMAN"))
                .andExpect(jsonPath("$.humanDecisions[0].decisionType").value("APPLICABILITY"))
                .andExpect(jsonPath("$.revision").value(2));
        verifyNoInteractions(programmedChecks);
        assertThat(jdbc.queryForMap("""
                SELECT proposed_status::text AS proposed, applicability_decision::text AS automatic,
                       applicability_origin::text AS origin FROM verification_results WHERE id = ?
                """, saved.id()))
                .containsEntry("proposed", "REVIEW_REQUIRED")
                .containsEntry("automatic", "UNDETERMINED")
                .containsEntry("origin", "DETERMINISTIC");
        mockMvc.perform(applicabilityRequest(fixture.runId(), saved.id(), fixture.userId(), "ADMIN",
                applicabilityBody(1, "APPLIES", "Segunda decisión")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM human_decisions WHERE result_id = ?",
                Long.class, saved.id())).isEqualTo(1);
    }

    @Test
    void resolvesAppliesForOneTicketAndKeepsItsSiblingAndRunProgress() throws Exception {
        Fixture fixture = createFixture(true);
        VerificationResult ticket204 = results.saveEvaluatedResult(command(fixture,
                fixture.ticketExpectation(), "204", ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.UNDETERMINED, List.of(), List.of(), List.of()));
        VerificationResult ticket205 = results.saveEvaluatedResult(command(fixture,
                fixture.ticketExpectation(), "205", ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.UNDETERMINED, List.of(), List.of(), List.of()));
        completeRun(fixture.runId());
        when(programmedChecks.assess(any(), any(), eq(ApplicabilityDecision.APPLIES), eq("204"), any(), any()))
                .thenReturn(new ProgrammedEvidenceCheckService.Assessment(fixture.ticketExpectation(), "204",
                        ResultStatus.REVIEW_REQUIRED, "Redmine no puede demostrar el cambio", List.of("Sin historial"),
                        List.of()));

        mockMvc.perform(applicabilityRequest(fixture.runId(), ticket204.id(), fixture.userId(), "ADMIN",
                applicabilityBody(1, "APPLIES", "El ticket 204 fue una corrección.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectTicketId").value("204"))
                .andExpect(jsonPath("$.proposedStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.effectiveStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.applicability.decision").value("APPLIES"))
                .andExpect(jsonPath("$.applicability.origin").value("HUMAN"))
                .andExpect(jsonPath("$.revision").value(2));
        assertThat(resultRepository.findByRunAndId(fixture.runId(), ticket205.id()).orElseThrow().revision())
                .isEqualTo(1);
        assertThat(resultRepository.progress(fixture.runId()).resultCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM human_decisions WHERE result_id = ?",
                Long.class, ticket205.id())).isZero();
    }

    @Test
    void appliesUsesTheRecheckOutcomeWithoutReplacingTheAutomaticProposal() throws Exception {
        for (ResultStatus outcome : List.of(ResultStatus.COMPLIES, ResultStatus.DOES_NOT_COMPLY,
                ResultStatus.REVIEW_REQUIRED)) {
            Fixture fixture = createFixture(true);
            VerificationResult pending = results.saveEvaluatedResult(command(fixture,
                    fixture.documentExpectations().getFirst(), null, ResultStatus.REVIEW_REQUIRED,
                    ApplicabilityDecision.UNDETERMINED, List.of(), List.of(), List.of()));
            completeRun(fixture.runId());
            when(programmedChecks.assess(any(), any(), eq(ApplicabilityDecision.APPLIES), eq(null), any(), any()))
                    .thenReturn(new ProgrammedEvidenceCheckService.Assessment(
                            fixture.documentExpectations().getFirst(), null, outcome,
                            "Resultado de la comprobación", List.of(), List.of()));

            mockMvc.perform(applicabilityRequest(fixture.runId(), pending.id(), fixture.userId(), "ADMIN",
                    applicabilityBody(1, "APPLIES", "El caso requiere el registro.")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(pending.id().toString()))
                    .andExpect(jsonPath("$.proposedStatus").value("REVIEW_REQUIRED"))
                    .andExpect(jsonPath("$.effectiveStatus").value(outcome.name()))
                    .andExpect(jsonPath("$.applicability.origin").value("HUMAN"))
                    .andExpect(jsonPath("$.revision").value(2));
            assertThat(jdbc.queryForObject("""
                    SELECT applicability_decision::text FROM verification_results WHERE id = ?
                    """, String.class, pending.id())).isEqualTo("UNDETERMINED");
        }
    }

    @Test
    void applicabilityEndpointEnforcesAuthorizationStateAndInput() throws Exception {
        Fixture fixture = createFixture(true);
        VerificationResult pending = results.saveEvaluatedResult(command(fixture,
                fixture.documentExpectations().getFirst(), null, ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.UNDETERMINED, List.of(), List.of(), List.of()));
        String valid = applicabilityBody(1, "NOT_APPLICABLE", "No corresponde.");
        mockMvc.perform(post("/api/v1/verifications/{runId}/results/{resultId}/applicability",
                fixture.runId(), pending.id()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(applicabilityRequest(fixture.runId(), pending.id(), fixture.userId(), "READER", valid))
                .andExpect(status().isForbidden());
        mockMvc.perform(applicabilityRequest(fixture.runId(), pending.id(), fixture.userId(), "ADMIN", valid))
                .andExpect(status().isConflict());
        completeRun(fixture.runId());
        mockMvc.perform(applicabilityRequest(fixture.runId(), UUID.randomUUID(), fixture.userId(), "ADMIN", valid))
                .andExpect(status().isNotFound());
        mockMvc.perform(applicabilityRequest(UUID.randomUUID(), pending.id(), fixture.userId(), "ADMIN", valid))
                .andExpect(status().isNotFound());
        for (String invalid : List.of("{}", applicabilityBody(1, "UNDETERMINED", "x"),
                applicabilityBody(1, "APPLIES", "   "),
                "{\"decision\":\"APPLIES\",\"rationale\":\"x\"}")) {
            mockMvc.perform(applicabilityRequest(fixture.runId(), pending.id(), fixture.userId(), "ADMIN", invalid))
                    .andExpect(status().isBadRequest());
        }
        VerificationResult always = results.saveEvaluatedResult(command(fixture,
                fixture.documentExpectations().get(1), null, ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.UNDETERMINED, List.of(), List.of(), List.of()));
        mockMvc.perform(applicabilityRequest(fixture.runId(), always.id(), fixture.userId(), "ADMIN", valid))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void applicabilityDecisionAndResultUpdateRollBackTogetherOnDatabaseFailure() {
        Fixture fixture = createFixture(true);
        VerificationResult saved = results.saveEvaluatedResult(command(fixture,
                fixture.documentExpectations().getFirst(), null, ResultStatus.REVIEW_REQUIRED,
                ApplicabilityDecision.UNDETERMINED, List.of(), List.of(), List.of()));
        completeRun(fixture.runId());
        assertThrows(DataIntegrityViolationException.class, () -> resultRepository.saveApplicabilityDecision(
                fixture.runId(), saved.id(), 1, ApplicabilityDecision.NOT_APPLICABLE,
                "No corresponde.", UUID.randomUUID(), DECISION_TIME,
                new VerificationResultRecheck(ResultStatus.NOT_APPLICABLE, "No aplica", List.of(), List.of())));
        assertThat(resultRepository.findByRunAndId(fixture.runId(), saved.id()).orElseThrow().revision())
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM human_decisions WHERE result_id = ?",
                Long.class, saved.id())).isZero();
    }

    private MockHttpServletRequestBuilder applicabilityRequest(UUID runId, UUID resultId,
            UUID authenticatedUser, String role, String body) {
        return post("/api/v1/verifications/{runId}/results/{resultId}/applicability", runId, resultId)
                .with(jwt().jwt(token -> token.subject(authenticatedUser.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String applicabilityBody(int revision, String decision, String rationale) {
        return """
                {"revision":%d,"decision":"%s","rationale":"%s"}
                """.formatted(revision, decision, rationale);
    }

    private MockHttpServletRequestBuilder decisionRequest(
            UUID runId,
            UUID resultId,
            UUID authenticatedUser,
            String role,
            String body
    ) {
        return put("/api/v1/verifications/{runId}/results/{resultId}/decision", runId, resultId)
                .with(jwt().jwt(token -> token.subject(authenticatedUser.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String decisionBody(int revision, String status, String rationale) {
        return """
                {"revision":%d,"finalStatus":"%s","rationale":"%s"}
                """.formatted(revision, status, rationale);
    }

    private List<VerificationResult> resultsForCompletedFixture(Fixture fixture) {
        completeRun(fixture.runId());
        return resultQueries.findCompletedRunResults(fixture.runId(), 0, 100).items();
    }

    @Test
    void storesTheInitialRedmineTicketAndRestrictsItsSnapshotToAdministrators() throws Exception {
        Fixture fixture = createFixture();
        var base = command(fixture, fixture.ticketExpectation(), "204", ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES, List.of(), List.of(), List.of());
        var observedAt = Instant.parse("2026-10-26T12:00:00Z");
        var ticket = new TicketSnapshot("204", "1", "Proyecto", "Solicitud de cambio", "Open", "1",
                "Abierto", false, "Descripción revisada", "Equipo", observedAt, observedAt,
                List.of(new TicketSnapshot.CustomField("7", "Prioridad", "Alta")), List.of());
        var withObservation = new VerificationResultWriteCommand(base.runId(), base.expectationId(),
                base.macroActivityId(), base.activityId(), base.displayName(), base.subjectTicketId(),
                base.applicabilityDecision(), base.applicabilityOrigin(), base.applicabilityRationale(),
                base.applicabilityLimitations(), base.proposedStatus(), base.rationale(), base.limitations(),
                base.evidence(), ticket, observedAt);
        var saved = results.saveEvaluatedResult(withObservation);

        assertThat(resultRepository.findTicketObservation(fixture.runId(), saved.id())).isPresent();
        mockMvc.perform(get("/api/v1/verifications/{runId}/results/{resultId}/ticket-observation",
                        fixture.runId(), saved.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.subject").value("Solicitud de cambio"))
                .andExpect(jsonPath("$.ticket.customFields[0].value").value("Alta"));
        mockMvc.perform(get("/api/v1/verifications/{runId}/results/{resultId}/ticket-observation",
                        fixture.runId(), saved.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isForbidden());
    }

    private VerificationResultWriteCommand command(
            Fixture fixture,
            UUID expectationId,
            String ticketId,
            ResultStatus status,
            ApplicabilityDecision applicability,
            List<String> applicabilityLimitations,
            List<String> limitations,
            List<VerificationResultWriteCommand.EvidenceReference> evidence
    ) {
        int expectationNumber = fixture.documentExpectations().indexOf(expectationId) + 1;
        boolean ticket = expectationId.equals(fixture.ticketExpectation());
        return new VerificationResultWriteCommand(
                fixture.runId(),
                expectationId,
                fixture.macroActivityId(),
                expectationId.equals(fixture.documentExpectations().get(3)) ? fixture.activityId() : null,
                ticket ? "Ticket expectation" : "Document expectation " + expectationNumber,
                ticketId,
                applicability,
                DecisionOrigin.DETERMINISTIC,
                "Applicability assessment rationale",
                applicabilityLimitations,
                status,
                "Evaluation rationale for " + status,
                limitations,
                evidence
        );
    }

    private Fixture createFixture() {
        return createFixture(false);
    }

    private Fixture createFixture(boolean pendingApplicability) {
        return createFixture(pendingApplicability, false);
    }

    private Fixture createFixture(boolean pendingApplicability, boolean thirdCaseDependent) {
        UUID userId = UUID.randomUUID();
        UUID processId = jdbc.query(
                "SELECT id FROM processes WHERE code = 'START_UP'::process_code",
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        ).stream().findFirst().orElseGet(() -> {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO processes (id, code, name, m2_enabled) VALUES (?, 'START_UP'::process_code, 'Start Up', true)",
                    id
            );
            return id;
        });
        UUID versionId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        UUID otherMacroId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID outOfScopeExpectationId = UUID.randomUUID();
        UUID documentRegistryId = UUID.randomUUID();
        UUID ticketRegistryId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();

        jdbc.update(
                "INSERT INTO users (id, email, display_name, password_hash, role) VALUES (?, ?, 'Result tester', 'unused', 'ADMIN'::role_enum)",
                userId,
                "result-test-" + suffix + "@example.invalid"
        );
        jdbc.update(
                """
                INSERT INTO process_versions (
                    id, process_id, official_version, objective, owner, version_date, status, revision, published_at
                ) VALUES (?, ?, ?, 'Verification result fixture', 'TEMA', ?, 'DRAFT'::process_status, 1, NULL)
                """,
                versionId,
                processId,
                "1.0-" + suffix,
                LocalDate.now()
        );
        jdbc.update(
                "INSERT INTO macro_activities (id, process_version_id, name, description, \"order\") VALUES (?, ?, 'Manage evidence', '', 1)",
                macroId,
                versionId
        );
        jdbc.update(
                "INSERT INTO macro_activities (id, process_version_id, name, description, \"order\") VALUES (?, ?, 'Other activity', '', 2)",
                otherMacroId,
                versionId
        );
        jdbc.update(
                """
                INSERT INTO activities (id, macro_activity_id, name, responsible, instructions, "order")
                VALUES (?, ?, 'Review evidence', 'Consultor', 'Review the evidence', 1)
                """,
                activityId,
                macroId
        );
        jdbc.update(
                """
                INSERT INTO registry_definitions (id, key, canonical_name, evidence_type, official_nomenclature, preloaded)
                VALUES (?, ?, 'Document evidence', 'DOCUMENT'::evidence_type, 'Fixture.pdf', true)
                """,
                documentRegistryId,
                "RESULT-DOC-" + suffix
        );
        jdbc.update(
                """
                INSERT INTO registry_definitions (id, key, canonical_name, evidence_type, preloaded)
                VALUES (?, ?, 'Ticket evidence', 'TICKET'::evidence_type, true)
                """,
                ticketRegistryId,
                "RESULT-TICKET-" + suffix
        );

        List<UUID> documentExpectations = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            UUID expectationId = UUID.randomUUID();
            documentExpectations.add(expectationId);
            jdbc.update(
                    """
                    INSERT INTO evidence_expectations (
                        id, macro_activity_id, activity_id, registry_definition_id, display_name,
                        applicability, cardinality, location, "order"
                    ) VALUES (?, ?, ?, ?, ?, ?::applicability_mode,
                              'ONE'::cardinality_enum, ?::evidence_location, ?)
                    """,
                    expectationId,
                    macroId,
                    index == 3 ? activityId : null,
                    documentRegistryId,
                    "Document expectation " + (index + 1),
                    (pendingApplicability && index == 0) || (thirdCaseDependent && index == 2)
                            ? "CASE_DEPENDENT" : "ALWAYS",
                    index == 3 ? "ACTIVITY_RECORD" : "INPUT",
                    index + 1
            );
        }
        jdbc.update(
                """
                INSERT INTO evidence_expectations (
                    id, macro_activity_id, registry_definition_id, display_name,
                    applicability, cardinality, location, "order"
                ) VALUES (?, ?, ?, 'Out-of-scope expectation', 'ALWAYS'::applicability_mode,
                          'ONE'::cardinality_enum, 'INPUT'::evidence_location, 1)
                """,
                outOfScopeExpectationId,
                otherMacroId,
                documentRegistryId
        );
        UUID ticketExpectationId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO evidence_expectations (
                    id, macro_activity_id, registry_definition_id, display_name,
                    applicability, cardinality, location, "order"
                ) VALUES (?, ?, ?, 'Ticket expectation', ?::applicability_mode,
                          'MULTIPLE'::cardinality_enum, 'INPUT'::evidence_location, 5)
                """,
                ticketExpectationId,
                macroId,
                ticketRegistryId,
                pendingApplicability ? "CASE_DEPENDENT" : "ALWAYS"
        );
        jdbc.update(
                """
                UPDATE process_versions
                SET status = 'PUBLISHED'::process_status, revision = 2, published_at = NOW()
                WHERE id = ?
                """,
                versionId
        );
        jdbc.update(
                """
                INSERT INTO services (id, name, client, service_type, status, revision)
                VALUES (?, 'Result service', 'Client', 'SUPPORT_ERP'::service_type, 'ACTIVE'::service_status, 1)
                """,
                serviceId
        );
        jdbc.update(
                "INSERT INTO service_process_versions (service_id, process_id, process_version_id) VALUES (?, ?, ?)",
                serviceId,
                processId,
                versionId
        );
        jdbc.update(
                """
                INSERT INTO verification_runs (
                    id, service_id, process_version_id, started_by, service_revision_snapshot,
                    scope, macro_activity_id, status
                ) VALUES (?, ?, ?, ?, 1, 'MACRO_ACTIVITY'::verification_scope,
                          ?, 'RUNNING'::run_status)
                """,
                runId,
                serviceId,
                versionId,
                userId,
                macroId
        );
        jdbc.update(
                "INSERT INTO verification_ticket_selections (run_id, group_id, ticket_id) VALUES (?, ?, '204'), (?, ?, '205')",
                runId,
                "ticket-group:" + macroId,
                runId,
                "ticket-group:" + macroId
        );
        return new Fixture(
                userId,
                processId,
                versionId,
                macroId,
                otherMacroId,
                activityId,
                serviceId,
                runId,
                List.copyOf(documentExpectations),
                ticketExpectationId,
                outOfScopeExpectationId
        );
    }

    private void completeRun(UUID runId) {
        jdbc.update(
                "UPDATE verification_runs SET status = 'COMPLETED'::run_status, finished_at = NOW() WHERE id = ?",
                runId
        );
    }

    private record Fixture(
            UUID userId,
            UUID processId,
            UUID processVersionId,
            UUID macroActivityId,
            UUID otherMacroActivityId,
            UUID activityId,
            UUID serviceId,
            UUID runId,
            List<UUID> documentExpectations,
            UUID ticketExpectation,
            UUID outOfScopeExpectation
    ) {
    }
}
