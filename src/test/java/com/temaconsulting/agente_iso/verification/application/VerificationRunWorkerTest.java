package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultPort;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultProgress;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.application.port.RedmineTicketReader;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelection;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class VerificationRunWorkerTest {
    private final VerificationRunRepository runs = mock(VerificationRunRepository.class);
    private final VerificationRunWorkLoader loader = mock(VerificationRunWorkLoader.class);
    private final ApplicabilityPolicy applicability = mock(ApplicabilityPolicy.class);
    private final ProgrammedEvidenceCheckService checks = mock(ProgrammedEvidenceCheckService.class);
    private final VerificationResultPort results = mock(VerificationResultPort.class);
    private final RedmineTicketReader tickets = mock(RedmineTicketReader.class);
    private final VerificationRunWorker worker = new VerificationRunWorker(runs, loader, applicability,
            checks, results, tickets);

    @Test
    void persistsOneResultPerSelectedTicketBeforeCompleting() {
        UUID runId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        UUID expectationId = UUID.randomUUID();
        String groupId = TicketSelectionGroup.stableIdFor(macroId);
        var selected = new ValidatedTicketSelections(List.of(
                new ValidatedTicketSelection(groupId, macroId, List.of("204", "205"))));
        VerificationRunSnapshot run = run(runId, selected, 2);
        var expectation = new VerificationContext.ExpectationContext(expectationId, 1, macroId, null,
                EvidenceLocation.INPUT, "Ticket actualizado", ApplicabilityMode.ALWAYS, Cardinality.MULTIPLE,
                new VerificationContext.RegistryContext(UUID.randomUUID(), "TICKET", "Ticket",
                        EvidenceType.TICKET, null), groupId);
        when(runs.markRunning(runId)).thenReturn(true);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(run));
        when(loader.load(run)).thenReturn(new VerificationRunWorkLoader.Work(service(run), List.of(),
                List.of(new VerificationRunWorkLoader.WorkItem(expectation, List.of()))));
        when(applicability.assess(any())).thenReturn(ApplicabilityAssessment.deterministic(
                ApplicabilityDecision.APPLIES, "Siempre aplicable"));
        when(checks.assess(eq(expectation), any(), eq(ApplicabilityDecision.APPLIES), any(), any(), any()))
                .thenAnswer(call -> new ProgrammedEvidenceCheckService.Assessment(expectationId,
                        call.getArgument(3), ResultStatus.REVIEW_REQUIRED, "Revisión humana",
                        List.of(), List.of()));
        when(results.progress(runId)).thenReturn(new VerificationResultProgress(2, 2));
        when(runs.markCompleted(runId)).thenReturn(true);

        worker.process(runId);

        var saved = org.mockito.Mockito.mockingDetails(results).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("saveEvaluatedResult"))
                .map(invocation -> (VerificationResultWriteCommand) invocation.getArgument(0))
                .toList();
        assertThat(saved).extracting(VerificationResultWriteCommand::subjectTicketId)
                .containsExactly("204", "205");
        verify(runs).markCompleted(runId);
        verify(runs, never()).markFailed(any(), any());
    }

    @Test
    void repeatedDocumentInOneMacroKeepsSeparateResultsWithOneApplicabilityDecision() {
        UUID runId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        UUID registryId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID outputId = UUID.randomUUID();
        VerificationRunSnapshot run = run(runId, new ValidatedTicketSelections(List.of()), 2);
        var registry = new VerificationContext.RegistryContext(registryId, "ARIBA", "Funcionalidades Ariba",
                EvidenceType.DOCUMENT, "Funcionalidades Ariba");
        var record = new VerificationContext.ExpectationContext(recordId, 1, macroId, UUID.randomUUID(),
                EvidenceLocation.ACTIVITY_RECORD, "Funcionalidades Ariba (si aplica)",
                ApplicabilityMode.CASE_DEPENDENT, Cardinality.ONE, registry, null);
        var output = new VerificationContext.ExpectationContext(outputId, 2, macroId, null,
                EvidenceLocation.OUTPUT, "Funcionalidades Ariba (si aplica)",
                ApplicabilityMode.CASE_DEPENDENT, Cardinality.ONE, registry, null);
        when(runs.markRunning(runId)).thenReturn(true);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(run));
        when(loader.load(run)).thenReturn(new VerificationRunWorkLoader.Work(service(run), List.of(), List.of(
                new VerificationRunWorkLoader.WorkItem(record, List.of("Relevar Ariba")),
                new VerificationRunWorkLoader.WorkItem(output, List.of()))));
        when(applicability.assess(any())).thenReturn(ApplicabilityAssessment.deterministic(
                ApplicabilityDecision.APPLIES, "El caso requiere Ariba"));
        when(checks.assess(any(), any(), eq(ApplicabilityDecision.APPLIES), eq(null), any(), any()))
                .thenAnswer(call -> new ProgrammedEvidenceCheckService.Assessment(
                        ((VerificationContext.ExpectationContext) call.getArgument(0)).expectationId(),
                        null, ResultStatus.COMPLIES, "Documento encontrado", List.of(), List.of()));
        when(results.progress(runId)).thenReturn(new VerificationResultProgress(2, 2));
        when(runs.markCompleted(runId)).thenReturn(true);

        worker.process(runId);

        verify(applicability, times(1)).assess(any());
        var saved = org.mockito.Mockito.mockingDetails(results).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("saveEvaluatedResult"))
                .map(invocation -> (VerificationResultWriteCommand) invocation.getArgument(0)).toList();
        assertThat(saved).extracting(VerificationResultWriteCommand::expectationId)
                .containsExactly(recordId, outputId);
        verify(runs).markCompleted(runId);
    }

    @Test
    void internalFailureLeavesRunFailedWithAlreadyPersistedProgress() {
        UUID runId = UUID.randomUUID();
        VerificationRunSnapshot run = run(runId, new ValidatedTicketSelections(List.of()), 1);
        UUID macroId = UUID.randomUUID();
        var expectation = new VerificationContext.ExpectationContext(UUID.randomUUID(), 1, macroId, null,
                EvidenceLocation.INPUT, "Documento", ApplicabilityMode.ALWAYS, Cardinality.ONE,
                new VerificationContext.RegistryContext(UUID.randomUUID(), "DOC", "Documento",
                        EvidenceType.DOCUMENT, "DOC"), null);
        when(runs.markRunning(runId)).thenReturn(true);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(run));
        when(loader.load(run)).thenReturn(new VerificationRunWorkLoader.Work(service(run), List.of(),
                List.of(new VerificationRunWorkLoader.WorkItem(expectation, List.of()))));
        when(applicability.assess(any())).thenReturn(ApplicabilityAssessment.deterministic(
                ApplicabilityDecision.APPLIES, "Siempre aplicable"));
        when(checks.assess(eq(expectation), any(), eq(ApplicabilityDecision.APPLIES), eq(null), any(), any()))
                .thenThrow(new IllegalStateException("internal failure"));

        worker.process(runId);

        verify(runs).markFailed(eq(runId), eq("La verificación no pudo completarse por un error interno."));
        verify(runs, never()).markCompleted(any());
        verify(results, never()).saveEvaluatedResult(any());
    }

    private static VerificationRunSnapshot run(UUID id, ValidatedTicketSelections selections, int total) {
        return new VerificationRunSnapshot(id, UUID.randomUUID(), ServiceType.SOFTWARE_FACTORY, 2,
                UUID.randomUUID(), "folder", "Folder", "project", "Project", VerificationScope.PROCESS,
                null, null, "RUNNING", UUID.randomUUID(), Instant.parse("2026-10-14T10:00:00Z"),
                null, null, total, selections);
    }

    private static VerificationContext.ServiceContext service(VerificationRunSnapshot run) {
        return new VerificationContext.ServiceContext(run.serviceId(), run.serviceTypeSnapshot(),
                run.serviceRevisionSnapshot(), VerificationContext.Source.configured("folder", "Folder"),
                VerificationContext.Source.configured("project", "Project"));
    }
}
