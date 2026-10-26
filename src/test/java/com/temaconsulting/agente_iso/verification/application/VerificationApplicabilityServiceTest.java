package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultDecisionFailure;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultRepository;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelection;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VerificationApplicabilityServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private final VerificationRunRepository runs = mock(VerificationRunRepository.class);
    private final VerificationRunWorkLoader loader = mock(VerificationRunWorkLoader.class);
    private final VerificationResultRepository results = mock(VerificationResultRepository.class);
    private final ProgrammedEvidenceCheckService checks = mock(ProgrammedEvidenceCheckService.class);
    private final VerificationApplicabilityService service = new VerificationApplicabilityService(
            runs, loader, results, checks, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void notApplicableRecordsOnlyOneDecisionAndDoesNotReadSources() {
        Fixture f = fixture(EvidenceType.DOCUMENT, null, ApplicabilityMode.CASE_DEPENDENT);
        service.resolve(f.runId, f.resultId, 1, ApplicabilityDecision.NOT_APPLICABLE, "  No hubo desarrollo  ", f.userId);
        verifyNoInteractions(checks);
        verify(results).saveApplicabilityDecision(eq(f.runId), eq(f.resultId), eq(1),
                eq(ApplicabilityDecision.NOT_APPLICABLE), eq("No hubo desarrollo"), eq(f.userId), eq(NOW),
                org.mockito.ArgumentMatchers.argThat(recheck ->
                        recheck.status() == ResultStatus.NOT_APPLICABLE && recheck.evidence().isEmpty()));
    }

    @Test
    void appliesChecksOnlyTheSelectedTicketAndDoesNotAssumeCompliance() {
        Fixture f = fixture(EvidenceType.TICKET, "204", ApplicabilityMode.CASE_DEPENDENT);
        when(checks.assess(eq(f.expectation), eq(f.service), eq(ApplicabilityDecision.APPLIES), eq("204"), any(), any()))
                .thenReturn(new ProgrammedEvidenceCheckService.Assessment(f.expectation.expectationId(), "204",
                        ResultStatus.REVIEW_REQUIRED, "La fuente no permite comprobarlo", List.of("Sin acceso"), List.of()));
        service.resolve(f.runId, f.resultId, 1, ApplicabilityDecision.APPLIES, "Fue una corrección", f.userId);
        verify(checks).assess(f.expectation, f.service, ApplicabilityDecision.APPLIES, "204", List.of(), List.of());
        verify(results).saveApplicabilityDecision(eq(f.runId), eq(f.resultId), eq(1),
                eq(ApplicabilityDecision.APPLIES), eq("Fue una corrección"), eq(f.userId), eq(NOW),
                org.mockito.ArgumentMatchers.argThat(recheck ->
                        recheck.status() == ResultStatus.REVIEW_REQUIRED
                                && recheck.limitations().equals(List.of("Sin acceso"))));
    }

    @Test
    void rejectsAnAlwaysExpectationBeforeCheckingAnything() {
        Fixture f = fixture(EvidenceType.DOCUMENT, null, ApplicabilityMode.ALWAYS);
        assertThatThrownBy(() -> service.resolve(f.runId, f.resultId, 1,
                ApplicabilityDecision.APPLIES, "Razón", f.userId))
                .isInstanceOf(VerificationResultDecisionFailure.class)
                .extracting("kind").isEqualTo(VerificationResultDecisionFailure.Kind.INCOMPATIBLE_APPLICABILITY);
        verifyNoInteractions(checks);
        verify(results, never()).saveApplicabilityDecision(any(), any(), eq(1), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsStaleRevisionBeforeCheckingSources() {
        Fixture f = fixture(EvidenceType.DOCUMENT, null, ApplicabilityMode.CASE_DEPENDENT);
        assertThatThrownBy(() -> service.resolve(f.runId, f.resultId, 2,
                ApplicabilityDecision.APPLIES, "Razón", f.userId))
                .isInstanceOf(VerificationResultDecisionFailure.class)
                .extracting("kind").isEqualTo(VerificationResultDecisionFailure.Kind.CONFLICT);
        verifyNoInteractions(checks);
    }

    @Test
    void unexpectedRecheckFailureLeavesTheResultUntouched() {
        Fixture f = fixture(EvidenceType.TICKET, "204", ApplicabilityMode.CASE_DEPENDENT);
        when(checks.assess(eq(f.expectation), eq(f.service), eq(ApplicabilityDecision.APPLIES), eq("204"), any(), any()))
                .thenThrow(new IllegalStateException("internal failure"));
        assertThatThrownBy(() -> service.resolve(f.runId, f.resultId, 1,
                ApplicabilityDecision.APPLIES, "Fue una corrección", f.userId))
                .isInstanceOf(IllegalStateException.class);
        verify(results, never()).saveApplicabilityDecision(any(), any(), eq(1), any(), any(), any(), any(), any());
    }

    private Fixture fixture(EvidenceType type, String ticketId, ApplicabilityMode mode) {
        UUID runId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        UUID expectationId = UUID.randomUUID();
        String groupId = "ticket-group:" + macroId;
        var selection = ticketId == null ? new ValidatedTicketSelections(List.of())
                : new ValidatedTicketSelections(List.of(new ValidatedTicketSelection(groupId, macroId, List.of("204", "205"))));
        var run = new VerificationRunSnapshot(runId, UUID.randomUUID(), ServiceType.SOFTWARE_FACTORY, 1,
                UUID.randomUUID(), "folder", "Folder", "project", "Project", VerificationScope.PROCESS,
                null, null, "COMPLETED", userId, NOW, NOW, null, 2, selection);
        var result = new VerificationResult(resultId, runId, expectationId, macroId, null,
                "Expected evidence", type, EvidenceLocation.INPUT, ticketId,
                ApplicabilityAssessment.notEvaluated("Pendiente", "Sin hechos"),
                ResultStatus.REVIEW_REQUIRED, ResultStatus.REVIEW_REQUIRED, "Pendiente",
                List.of(), List.of(), List.of(), 1);
        var expectation = new VerificationContext.ExpectationContext(expectationId, 1, macroId, null,
                EvidenceLocation.INPUT, "Expected evidence", mode, Cardinality.ONE,
                new VerificationContext.RegistryContext(UUID.randomUUID(), "KEY", "Expected evidence", type, "NAME"),
                ticketId == null ? null : groupId);
        var source = new VerificationContext.ServiceContext(run.serviceId(), run.serviceTypeSnapshot(),
                run.serviceRevisionSnapshot(), VerificationContext.Source.configured("folder", "Folder"),
                VerificationContext.Source.configured("project", "Project"));
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(results.findByRunAndId(runId, resultId)).thenReturn(Optional.of(result));
        when(loader.load(run)).thenReturn(new VerificationRunWorkLoader.Work(source, List.of(),
                List.of(new VerificationRunWorkLoader.WorkItem(expectation, List.of()))));
        return new Fixture(runId, resultId, userId, expectation, source);
    }

    private record Fixture(UUID runId, UUID resultId, UUID userId,
            VerificationContext.ExpectationContext expectation, VerificationContext.ServiceContext service) { }
}
