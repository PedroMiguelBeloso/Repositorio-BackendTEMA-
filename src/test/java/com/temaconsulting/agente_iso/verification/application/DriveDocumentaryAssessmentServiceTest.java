package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.Cardinality;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.Kind;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.Match;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.Problem;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.SearchRequest;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.SearchResult;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DriveDocumentaryAssessmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final SearchRequest ONE = new SearchRequest("service-folder", Kind.DOCUMENT,
            "Highlights.xls", Cardinality.ONE);
    private static final SearchRequest MULTIPLE = new SearchRequest("service-folder", Kind.DOCUMENT,
            "Highlights.xls", Cardinality.MULTIPLE);

    private final FakeSearch search = new FakeSearch();
    private final DriveDocumentaryAssessmentService service = new DriveDocumentaryAssessmentService(
            search, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void oneMatchProvesOnlyExistenceAndNomenclature() {
        search.result = SearchResult.found(List.of(match("file-1", "Documentos/Highlights.xls")), Cardinality.ONE);

        var assessment = service.assess(ONE, ApplicabilityDecision.APPLIES);

        assertThat(assessment.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(assessment.limitations()).anyMatch(text -> text.contains("no se revisó el contenido"));
        assertThat(assessment.rationale()).contains("no se revisó el contenido")
                .contains("la completitud")
                .contains("la aprobación");
        assertThat(assessment.evidence()).singleElement().satisfies(reference -> {
            assertThat(reference.source()).isEqualTo(IntegrationSource.DRIVE);
            assertThat(reference.kind()).isEqualTo(Kind.DOCUMENT);
            assertThat(reference.externalId()).isEqualTo("file-1");
            assertThat(reference.displayName()).isEqualTo("Highlights.xls");
            assertThat(reference.webUrl()).isEqualTo("https://drive.google.com/file/d/file-1/view");
            assertThat(reference.relativePath()).isEqualTo("Documentos/Highlights.xls");
            assertThat(reference.observedAt()).isEqualTo(NOW);
        });
        assertThat(search.lastRequest).isEqualTo(ONE);
    }

    @Test
    void folderEvidenceKeepsItsKindAndDoesNotClaimDocumentContent() {
        SearchRequest folderRequest = new SearchRequest("service-folder", Kind.FOLDER,
                "Repositorio de documentación", Cardinality.ONE);
        search.result = SearchResult.found(List.of(new Match("folder-1",
                "Repositorio de documentación", Kind.FOLDER,
                "https://drive.google.com/drive/folders/folder-1",
                "Repositorio de documentación")), Cardinality.ONE);

        var assessment = service.assess(folderRequest, ApplicabilityDecision.APPLIES);

        assertThat(assessment.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(assessment.evidence()).singleElement().satisfies(reference -> {
            assertThat(reference.kind()).isEqualTo(Kind.FOLDER);
            assertThat(reference.externalId()).isEqualTo("folder-1");
        });
        assertThat(assessment.limitations()).anyMatch(text -> text.contains("no se revisó el contenido"));
    }

    @Test
    void completeSearchWithoutMatchesIsNoncompliance() {
        search.result = SearchResult.absent();

        var assessment = service.assess(ONE, ApplicabilityDecision.APPLIES);

        assertThat(assessment.proposedStatus()).isEqualTo(ResultStatus.DOES_NOT_COMPLY);
        assertThat(assessment.evidence()).isEmpty();
    }

    @Test
    void twoMatchesForOneAreAmbiguousAndBothRemainVisible() {
        search.result = SearchResult.found(List.of(match("file-1", "A/Highlights.xls"),
                match("file-2", "B/Highlights.xls")), Cardinality.ONE);

        var assessment = service.assess(ONE, ApplicabilityDecision.APPLIES);

        assertThat(assessment.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(assessment.evidence()).extracting(reference -> reference.externalId())
                .containsExactly("file-1", "file-2");
        assertThat(assessment.limitations()).anyMatch(text -> text.contains("coincidencias"));
    }

    @Test
    void multipleAcceptsOneOrMoreButNotZero() {
        search.result = SearchResult.found(List.of(match("file-1", "A/Highlights.xls"),
                match("file-2", "B/Highlights.xls")), Cardinality.MULTIPLE);
        assertThat(service.assess(MULTIPLE, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.COMPLIES);

        search.result = SearchResult.found(List.of(match("file-1", "A/Highlights.xls")), Cardinality.MULTIPLE);
        assertThat(service.assess(MULTIPLE, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.COMPLIES);

        search.result = SearchResult.absent();
        assertThat(service.assess(MULTIPLE, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.DOES_NOT_COMPLY);
    }

    @Test
    void everySourceOrConfigurationProblemRequiresReviewRatherThanNoncompliance() {
        for (Problem problem : Problem.values()) {
            search.result = SearchResult.notCheckable(problem);

            var assessment = service.assess(ONE, ApplicabilityDecision.APPLIES);

            assertThat(assessment.proposedStatus()).as(problem.name()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
            assertThat(assessment.limitations()).as(problem.name()).hasSize(1);
            assertThat(assessment.rationale()).doesNotContain("file-1");
        }
    }

    @Test
    void incompleteSearchCannotProveComplianceEvenWithPartialCandidates() {
        search.result = new SearchResult(DriveEvidenceSearch.Outcome.NOT_CHECKABLE, Problem.INCOMPLETE,
                List.of(match("file-1", "Documentos/Highlights.xls")));

        var assessment = service.assess(ONE, ApplicabilityDecision.APPLIES);

        assertThat(assessment.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(assessment.evidence()).hasSize(1);
        assertThat(assessment.limitations()).anyMatch(text -> text.contains("antes de revisar todo"));
    }

    @Test
    void unresolvedOrExplicitlyExcludedExpectationsNeverQueryDrive() {
        var pending = service.assess(ONE, ApplicabilityDecision.UNDETERMINED);
        var excluded = service.assess(ONE, ApplicabilityDecision.NOT_APPLICABLE);

        assertThat(pending.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        assertThat(excluded.proposedStatus()).isEqualTo(ResultStatus.NOT_APPLICABLE);
        assertThat(pending.evidence()).isEmpty();
        assertThat(excluded.evidence()).isEmpty();
        assertThat(search.calls).isZero();
    }

    @Test
    void inconsistentFoundResultNeverProducesFalseCompliance() {
        search.result = new SearchResult(DriveEvidenceSearch.Outcome.FOUND, null, List.of());
        assertThat(service.assess(ONE, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.REVIEW_REQUIRED);
        search.result = new SearchResult(DriveEvidenceSearch.Outcome.FOUND, null,
                List.of(match("file-1", "A/Highlights.xls"), match("file-2", "B/Highlights.xls")));
        assertThat(service.assess(ONE, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.REVIEW_REQUIRED);
        search.result = SearchResult.found(List.of(new Match("file-1", "Different name",
                Kind.DOCUMENT, "https://drive.google.com/file/d/file-1/view", "Different name")),
                Cardinality.ONE);
        assertThat(service.assess(ONE, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.REVIEW_REQUIRED);
        SearchRequest nestedNomenclature = new SearchRequest("service-folder", Kind.DOCUMENT,
                "Documentos/Highlights.xls", Cardinality.ONE);
        search.result = SearchResult.found(List.of(new Match("file-1", "Highlights.xls",
                Kind.DOCUMENT, "https://drive.google.com/file/d/file-1/view", "Unexpected/Highlights.xls")),
                Cardinality.ONE);
        assertThat(service.assess(nestedNomenclature, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.REVIEW_REQUIRED);
        search.result = new SearchResult(DriveEvidenceSearch.Outcome.ABSENT, null,
                List.of(match("file-1", "A/Highlights.xls")));
        assertThat(service.assess(ONE, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.REVIEW_REQUIRED);
        search.result = new SearchResult(DriveEvidenceSearch.Outcome.ABSENT, Problem.PERMISSION_ERROR, List.of());
        assertThat(service.assess(ONE, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.REVIEW_REQUIRED);
        search.result = null;
        assertThat(service.assess(ONE, ApplicabilityDecision.APPLIES).proposedStatus())
                .isEqualTo(ResultStatus.REVIEW_REQUIRED);
    }

    private static Match match(String id, String relativePath) {
        return new Match(id, "Highlights.xls", Kind.DOCUMENT,
                "https://drive.google.com/file/d/" + id + "/view", relativePath);
    }

    private static final class FakeSearch implements DriveEvidenceSearch {
        private SearchResult result;
        private SearchRequest lastRequest;
        private int calls;

        @Override
        public SearchResult search(SearchRequest request) {
            lastRequest = request;
            calls++;
            return result;
        }
    }
}
