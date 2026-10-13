package com.temaconsulting.agente_iso.verification.infrastructure;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.identity.infrastructure.JsonAccessDeniedHandler;
import com.temaconsulting.agente_iso.identity.infrastructure.JsonAuthenticationEntryPoint;
import com.temaconsulting.agente_iso.identity.infrastructure.JwtRoleConverter;
import com.temaconsulting.agente_iso.identity.infrastructure.SecurityConfig;
import com.temaconsulting.agente_iso.shared.api.ApiExceptionHandler;
import com.temaconsulting.agente_iso.shared.api.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.config.Configuration;
import com.temaconsulting.agente_iso.verification.api.VerificationApiExceptionHandler;
import com.temaconsulting.agente_iso.verification.application.VerificationResultConflictException;
import com.temaconsulting.agente_iso.verification.application.VerificationResultPort;
import com.temaconsulting.agente_iso.verification.application.VerificationResultProgress;
import com.temaconsulting.agente_iso.verification.application.VerificationResultQueryFailure;
import com.temaconsulting.agente_iso.verification.application.VerificationResultQueryService;
import com.temaconsulting.agente_iso.verification.application.VerificationResultValidationException;
import com.temaconsulting.agente_iso.verification.application.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;
import com.temaconsulting.agente_iso.verification.domain.HumanDecisionType;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    VerificationResultQueryService resultQueries;

    @Autowired
    MockMvc mockMvc;

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
                          'REVIEW_REQUIRED'::result_status, 'Requested more review', ?)
                """,
                statusEventId,
                saved.id(),
                fixture.userId(),
                java.sql.Timestamp.from(newer)
        );
        jdbc.update(
                "UPDATE verification_results SET effective_status = 'REVIEW_REQUIRED'::result_status, revision = 2 WHERE id = ?",
                saved.id()
        );

        assertThat(results.progress(fixture.runId())).isEqualTo(new VerificationResultProgress(1, 1));
        assertThrows(
                VerificationResultQueryFailure.class,
                () -> results.progress(UUID.randomUUID())
        );
        VerificationResult replayed = results.saveEvaluatedResult(command(
                fixture,
                fixture.documentExpectations().get(0),
                null,
                ResultStatus.COMPLIES,
                ApplicabilityDecision.APPLIES,
                List.of(),
                List.of(),
                List.of()
        ));
        assertThat(replayed.id()).isEqualTo(saved.id());
        assertThat(replayed.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(replayed.effectiveStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(replayed.revision()).isEqualTo(2);
        assertThat(replayed.humanDecisions()).hasSize(2);
        completeRun(fixture.runId());
        List<VerificationResult> stored = resultsForCompletedFixture(fixture);
        VerificationResult reloaded = stored.getFirst();
        assertThat(reloaded.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(reloaded.effectiveStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(reloaded.revision()).isEqualTo(2);
        assertThat(reloaded.humanDecisions()).extracting("id")
                .containsExactly(applicabilityEventId, statusEventId);
        assertThat(reloaded.humanDecisions().getFirst().decisionType())
                .isEqualTo(HumanDecisionType.APPLICABILITY);
        mockMvc.perform(get("/api/v1/verifications/{runId}/results", fixture.runId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].humanDecisions[0].applicabilityDecision").value("APPLIES"))
                .andExpect(jsonPath("$.items[0].humanDecisions[0].finalStatus").doesNotExist())
                .andExpect(jsonPath("$.items[0].humanDecisions[1].finalStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.items[0].humanDecisions[1].applicabilityDecision").doesNotExist());
    }

    private List<VerificationResult> resultsForCompletedFixture(Fixture fixture) {
        completeRun(fixture.runId());
        return resultQueries.findCompletedRunResults(fixture.runId(), 0, 100).items();
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
                INSERT INTO registry_definitions (id, key, canonical_name, evidence_type, preloaded)
                VALUES (?, ?, 'Document evidence', 'DOCUMENT'::evidence_type, true)
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
                    ) VALUES (?, ?, ?, ?, ?, 'ALWAYS'::applicability_mode,
                              'ONE'::cardinality_enum, ?::evidence_location, ?)
                    """,
                    expectationId,
                    macroId,
                    index == 3 ? activityId : null,
                    documentRegistryId,
                    "Document expectation " + (index + 1),
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
                ) VALUES (?, ?, ?, 'Ticket expectation', 'ALWAYS'::applicability_mode,
                          'MULTIPLE'::cardinality_enum, 'INPUT'::evidence_location, 5)
                """,
                ticketExpectationId,
                macroId,
                ticketRegistryId
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
