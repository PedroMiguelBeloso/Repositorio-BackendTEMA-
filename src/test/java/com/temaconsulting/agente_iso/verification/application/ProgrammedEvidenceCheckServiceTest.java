package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch;
import com.temaconsulting.agente_iso.integration.application.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.ExpectationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.RegistryContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.ServiceContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgrammedEvidenceCheckServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final String PROJECT_ID = "redmine-project-9";
    private static final UUID EXPECTATION_ID = UUID.fromString("f509e42e-117a-4d61-bd8c-87d75d9c92aa");

    private final FakeSearch search = new FakeSearch();
    private final FakeTickets tickets = new FakeTickets();
    private final DriveDocumentaryAssessmentService driveAssessment =
            new DriveDocumentaryAssessmentService(search, Clock.fixed(NOW, ZoneOffset.UTC));
    private final ProgrammedEvidenceCheckService service = new ProgrammedEvidenceCheckService(
            driveAssessment, tickets, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void unresolvedOrExcludedApplicabilityNeverCallsEitherExternalReader() {
        var document = service.assess(expectation(EvidenceType.DOCUMENT, Cardinality.ONE),
                configuredService(), ApplicabilityDecision.NOT_APPLICABLE, null);
        var ticket = service.assess(expectation(EvidenceType.TICKET, Cardinality.ONE),
                configuredService(), ApplicabilityDecision.UNDETERMINED, "204");

        assertThat(document.proposedStatus()).isEqualTo(ResultStatus.NOT_APPLICABLE);
        assertThat(ticket.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(ticket.rationale()).contains("Primero falta resolver la aplicabilidad");
        assertThat(search.calls).isZero();
        assertThat(tickets.calls).isZero();
    }

    @Test
    void driveAssessmentUsesConfiguredFolderCatalogNomenclatureAndCardinalityAndReturnsTraceableReference() {
        search.result = DriveEvidenceSearch.SearchResult.found(List.of(
                new DriveEvidenceSearch.Match("drive-file-1", "Validación del Plan de Proyecto",
                        DriveEvidenceSearch.Kind.DOCUMENT, "https://drive.example/file/1",
                        "Plan/Validación del Plan de Proyecto")
        ), DriveEvidenceSearch.Cardinality.ONE);

        var result = service.assess(expectation(EvidenceType.DOCUMENT, Cardinality.ONE),
                configuredService(), ApplicabilityDecision.APPLIES, null);

        assertThat(result.expectationId()).isEqualTo(EXPECTATION_ID);
        assertThat(result.subjectTicketId()).isNull();
        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(search.calls).isEqualTo(1);
        assertThat(search.lastRequest).isEqualTo(new DriveEvidenceSearch.SearchRequest(
                "drive-folder-12", DriveEvidenceSearch.Kind.DOCUMENT,
                "Validación del Plan de Proyecto", DriveEvidenceSearch.Cardinality.ONE));
        assertThat(result.rationale()).contains("existencia y nomenclatura")
                .contains("no se revisó el contenido")
                .contains("la completitud")
                .contains("la aprobación");
        assertThat(result.evidenceReferences()).singleElement().satisfies(reference -> {
            assertThat(reference.kind()).isEqualTo(EvidenceType.DOCUMENT);
            assertThat(reference.source()).isEqualTo(IntegrationSource.DRIVE);
            assertThat(reference.externalId()).isEqualTo("drive-file-1");
            assertThat(reference.displayName()).isEqualTo("Validación del Plan de Proyecto");
            assertThat(reference.webUrl()).isEqualTo("https://drive.example/file/1");
            assertThat(reference.observedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void completeAbsenceDuplicateOnePermissionAndIncompleteSearchHaveConservativeStatuses() {
        var expectation = expectation(EvidenceType.DOCUMENT, Cardinality.ONE);
        search.result = DriveEvidenceSearch.SearchResult.absent();
        assertThat(assess(expectation).proposedStatus()).isEqualTo(ResultStatus.DOES_NOT_COMPLY);

        search.result = DriveEvidenceSearch.SearchResult.found(List.of(match("1"), match("2")),
                DriveEvidenceSearch.Cardinality.ONE);
        assertThat(assess(expectation).proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);

        search.result = DriveEvidenceSearch.SearchResult.notCheckable(DriveEvidenceSearch.Problem.PERMISSION_ERROR);
        assertThat(assess(expectation).proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);

        search.result = new DriveEvidenceSearch.SearchResult(DriveEvidenceSearch.Outcome.FOUND, null,
                List.of(new DriveEvidenceSearch.Match("wrong-kind", "Validación del Plan de Proyecto",
                        DriveEvidenceSearch.Kind.FOLDER, "https://drive.example/folder/wrong", "Plan")));
        var wrongKind = assess(expectation);
        assertThat(wrongKind.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(wrongKind.evidenceReferences()).singleElement()
                .satisfies(reference -> assertThat(reference.kind()).isEqualTo(EvidenceType.FOLDER));

        search.result = new DriveEvidenceSearch.SearchResult(DriveEvidenceSearch.Outcome.NOT_CHECKABLE,
                DriveEvidenceSearch.Problem.INCOMPLETE, List.of(match("partial")));
        var incomplete = assess(expectation);
        assertThat(incomplete.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(incomplete.evidenceReferences()).hasSize(1);
    }

    @Test
    void multipleAcceptsOneOrMoreMatchesAndMapsFolders() {
        var expectation = expectation(EvidenceType.DOCUMENT, Cardinality.MULTIPLE);
        search.result = DriveEvidenceSearch.SearchResult.found(List.of(match("1")),
                DriveEvidenceSearch.Cardinality.MULTIPLE);
        assertThat(assess(expectation).proposedStatus()).isEqualTo(ResultStatus.COMPLIES);

        search.result = DriveEvidenceSearch.SearchResult.found(List.of(match("1"), match("2")),
                DriveEvidenceSearch.Cardinality.MULTIPLE);
        assertThat(assess(expectation).proposedStatus()).isEqualTo(ResultStatus.COMPLIES);

        search.result = DriveEvidenceSearch.SearchResult.absent();
        assertThat(assess(expectation).proposedStatus()).isEqualTo(ResultStatus.DOES_NOT_COMPLY);

        search.result = DriveEvidenceSearch.SearchResult.found(List.of(new DriveEvidenceSearch.Match(
                "folder-1", "Validación del Plan de Proyecto", DriveEvidenceSearch.Kind.FOLDER,
                "https://drive.example/folder/1", "Validación del Plan de Proyecto")),
                DriveEvidenceSearch.Cardinality.ONE);
        var folder = service.assess(expectation(EvidenceType.FOLDER, Cardinality.ONE),
                configuredService(), ApplicabilityDecision.APPLIES, null);
        assertThat(search.lastRequest.kind()).isEqualTo(DriveEvidenceSearch.Kind.FOLDER);
        assertThat(folder.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
    }

    @Test
    void absentDriveFolderNeverClaimsEvidenceAbsence() {
        search.result = DriveEvidenceSearch.SearchResult.absent();
        var result = service.assess(expectation(EvidenceType.DOCUMENT, Cardinality.ONE),
                serviceWithoutDrive(), ApplicabilityDecision.APPLIES, null);

        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(result.limitations()).anyMatch(text -> text.contains("no está configurada"));
        assertThat(search.calls).isZero();
    }

    @Test
    void eachSelectedTicketIsReadOnlyFromTheConfiguredProjectAndRemainsASeparateResult() {
        tickets.snapshots.put("204", ticket("204", PROJECT_ID, "Planificar entrega", false,
                Instant.parse("2026-10-01T10:00:00Z")));
        tickets.snapshots.put("205", ticket("205", PROJECT_ID, "Cerrar implementación", true,
                Instant.parse("2026-10-02T10:00:00Z")));
        var expectation = expectation(EvidenceType.TICKET, Cardinality.MULTIPLE);

        var first = service.assess(expectation, configuredService(), ApplicabilityDecision.APPLIES, "204");
        var second = service.assess(expectation, configuredService(), ApplicabilityDecision.APPLIES, "205");

        assertThat(tickets.calls).isEqualTo(2);
        assertThat(tickets.queries).containsExactly(
                new TicketQuery(PROJECT_ID, "204"), new TicketQuery(PROJECT_ID, "205"));
        assertThat(first.expectationId()).isEqualTo(EXPECTATION_ID);
        assertThat(first.subjectTicketId()).isEqualTo("204");
        assertThat(first.evidenceReferences()).singleElement()
                .satisfies(reference -> assertThat(reference.externalId()).isEqualTo("204"));
        assertThat(second.subjectTicketId()).isEqualTo("205");
        assertThat(second.evidenceReferences()).singleElement()
                .satisfies(reference -> assertThat(reference.externalId()).isEqualTo("205"));
        assertThat(first.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(second.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
    }

    @Test
    void closedTicketWithoutDefinedValidationSignalAndUpdatedAtNeverComply() {
        tickets.snapshots.put("204", ticket("204", PROJECT_ID, "Ticket cerrado", true,
                Instant.parse("2026-10-02T10:00:00Z")));
        var result = service.assess(expectation(EvidenceType.TICKET, Cardinality.ONE),
                configuredService(), ApplicabilityDecision.APPLIES, "204");

        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(result.rationale()).contains("closed=true")
                .contains("updatedAt=2026-10-02T10:00:00Z")
                .contains("no demuestra")
                .contains("no identifica un campo o valor objetivo")
                .contains("No se interpretaron journals ni comentarios");
        assertThat(result.evidenceReferences()).singleElement().satisfies(reference -> {
            assertThat(reference.kind()).isEqualTo(EvidenceType.TICKET);
            assertThat(reference.source()).isEqualTo(IntegrationSource.REDMINE);
            assertThat(reference.ticketId()).isEqualTo("204");
            assertThat(reference.observedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void ticketReadFailureRetainsTechnicalCauseAndNeverBecomesComplianceOrAbsence() {
        tickets.failure = new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                "Redmine no disponible", List.of());
        var result = service.assess(expectation(EvidenceType.TICKET, Cardinality.ONE),
                configuredService(), ApplicabilityDecision.APPLIES, "204");

        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(result.subjectTicketId()).isEqualTo("204");
        assertThat(result.rationale()).contains("SOURCE_UNAVAILABLE").contains("Redmine no disponible");
        assertThat(result.evidenceReferences()).isEmpty();
    }

    @Test
    void ticketSnapshotMustPreserveSelectedIdAndConfiguredProject() {
        tickets.snapshots.put("204", ticket("205", "another-project", "Unexpected", false, null));

        var result = service.assess(expectation(EvidenceType.TICKET, Cardinality.ONE),
                configuredService(), ApplicabilityDecision.APPLIES, "204");

        assertThat(tickets.queries).containsExactly(new TicketQuery(PROJECT_ID, "204"));
        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(result.subjectTicketId()).isEqualTo("204");
        assertThat(result.limitations()).anyMatch(text -> text.contains("no coincide"));
    }

    @Test
    void imputationsAndToolRecordsRequireReviewUntilAReaderAndRuleExist() {
        for (EvidenceType type : List.of(EvidenceType.IMPUTATIONS, EvidenceType.TOOL_RECORD)) {
            var result = service.assess(expectation(type, Cardinality.ONE), configuredService(),
                    ApplicabilityDecision.APPLIES, null);

            assertThat(result.proposedStatus()).as(type.name()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
            assertThat(result.limitations()).anyMatch(text -> text.contains(type.name()));
        }
        assertThat(search.calls).isZero();
        assertThat(tickets.calls).isZero();
    }

    private ProgrammedEvidenceCheckService.Assessment assess(ExpectationContext expectation) {
        return service.assess(expectation, configuredService(), ApplicabilityDecision.APPLIES, null);
    }

    private static DriveEvidenceSearch.Match match(String id) {
        return new DriveEvidenceSearch.Match(id, "Validación del Plan de Proyecto",
                DriveEvidenceSearch.Kind.DOCUMENT, "https://drive.example/file/" + id,
                "Plan/Validación del Plan de Proyecto");
    }

    private static ExpectationContext expectation(EvidenceType type, Cardinality cardinality) {
        return new ExpectationContext(EXPECTATION_ID, 1, UUID.randomUUID(), null,
                EvidenceLocation.INPUT, "Nombre visible que no se usa como regla", ApplicabilityMode.ALWAYS,
                cardinality, new RegistryContext(UUID.randomUUID(), "test", "Registro de prueba",
                type, "Validación del Plan de Proyecto"), null);
    }

    private static ServiceContext configuredService() {
        return new ServiceContext(UUID.randomUUID(), ServiceType.SUPPORT_ERP, 1,
                VerificationContext.Source.configured("drive-folder-12", "Carpeta del servicio"),
                VerificationContext.Source.configured(PROJECT_ID, "Proyecto del servicio"));
    }

    private static ServiceContext serviceWithoutDrive() {
        return new ServiceContext(UUID.randomUUID(), ServiceType.SUPPORT_ERP, 1,
                VerificationContext.Source.missing(),
                VerificationContext.Source.configured(PROJECT_ID, "Proyecto del servicio"));
    }

    private static TicketSnapshot ticket(String id, String projectId, String subject, boolean closed, Instant updatedAt) {
        return new TicketSnapshot(id, projectId, "Proyecto TEMA", subject, closed ? "Closed" : "In Progress",
                null, null, closed, null, null, updatedAt, null, List.of(), List.of());
    }

    private static final class FakeSearch implements DriveEvidenceSearch {
        private SearchResult result;
        private SearchRequest lastRequest;
        private int calls;

        @Override
        public SearchResult search(SearchRequest request) {
            calls++;
            lastRequest = request;
            return result;
        }
    }

    private static final class FakeTickets implements RedmineTicketReader {
        private final Map<String, TicketSnapshot> snapshots = new HashMap<>();
        private final List<TicketQuery> queries = new ArrayList<>();
        private int calls;
        private ApiException failure;

        @Override
        public TicketSnapshot getTicket(String projectId, String ticketId) {
            calls++;
            queries.add(new TicketQuery(projectId, ticketId));
            if (failure != null) {
                throw failure;
            }
            return snapshots.get(ticketId);
        }
    }

    private record TicketQuery(String projectId, String ticketId) {
    }
}
