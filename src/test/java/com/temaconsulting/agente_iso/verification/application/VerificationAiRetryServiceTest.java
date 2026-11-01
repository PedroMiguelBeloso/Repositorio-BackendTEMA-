package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.application.port.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.application.model.AiRetryAttempt;
import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;
import com.temaconsulting.agente_iso.verification.application.port.AiRetryRepository;
import com.temaconsulting.agente_iso.verification.application.port.ApplicabilityAiPort;
import com.temaconsulting.agente_iso.verification.application.port.TicketEvidenceAiPort;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultRepository;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.domain.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationAiRetryServiceTest {
    @Test
    void doesNotRecordAnAiRetryWhenAConditionalDocumentHasNoCaseFacts() {
        UUID runId = UUID.randomUUID(), resultId = UUID.randomUUID(), expectationId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        var runs = mock(VerificationRunRepository.class);
        var loader = mock(VerificationRunWorkLoader.class);
        var results = mock(VerificationResultRepository.class);
        var retries = mock(AiRetryRepository.class);
        var tickets = mock(RedmineTicketReader.class);
        var applicabilityAi = mock(ApplicabilityAiPort.class);
        var ticketAi = mock(TicketEvidenceAiPort.class);
        var checks = mock(ProgrammedEvidenceCheckService.class);
        var run = mock(VerificationRunSnapshot.class);
        when(run.status()).thenReturn("COMPLETED");
        when(run.ticketSelections()).thenReturn(new ValidatedTicketSelections(List.of()));
        when(run.serviceTypeSnapshot()).thenReturn(ServiceType.SOFTWARE_FACTORY);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        var current = new VerificationResult(resultId, runId, expectationId, macroId, null,
                "Riesgos comerciales", EvidenceType.DOCUMENT, EvidenceLocation.INPUT, null,
                ApplicabilityAssessment.notEvaluated("No hay datos", "No se recibieron datos del caso"),
                ResultStatus.REVIEW_REQUIRED, ResultStatus.REVIEW_REQUIRED,
                "Aplicabilidad pendiente", List.of(), List.of(), List.of(), 1);
        when(results.findByRunAndId(runId, resultId)).thenReturn(Optional.of(current));
        var expectation = new VerificationContext.ExpectationContext(expectationId, 1, macroId, null,
                EvidenceLocation.INPUT, "Riesgos comerciales", ApplicabilityMode.CASE_DEPENDENT,
                Cardinality.ONE, new VerificationContext.RegistryContext(UUID.randomUUID(), "risk",
                "Riesgos comerciales", EvidenceType.DOCUMENT, null), null);
        when(loader.load(run)).thenReturn(new VerificationRunWorkLoader.Work(
                new VerificationContext.ServiceContext(UUID.randomUUID(), ServiceType.SOFTWARE_FACTORY, 1,
                        VerificationContext.Source.missing(), VerificationContext.Source.missing()),
                List.of(), List.of(new VerificationRunWorkLoader.WorkItem(expectation, List.of()))));
        var service = new VerificationAiRetryService(runs, loader, results, retries, tickets,
                applicabilityAi, ticketAi, checks, Clock.systemUTC(), "GEMINI", "test-key");

        assertThatThrownBy(() -> service.retry(runId, resultId, 1, UUID.randomUUID()))
                .isInstanceOf(com.temaconsulting.agente_iso.shared.application.error.ApiException.class)
                .hasMessageContaining("No hay hechos del caso");
        verifyNoInteractions(applicabilityAi, retries);
    }

    @Test
    void reReadsTicketAndPreservesHumanDecisionWithImmutablePreviousProposal() {
        UUID runId = UUID.randomUUID(), resultId = UUID.randomUUID(), expectationId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID(), actor = UUID.randomUUID();
        var runs = mock(VerificationRunRepository.class);
        var loader = mock(VerificationRunWorkLoader.class);
        var results = mock(VerificationResultRepository.class);
        var retries = mock(AiRetryRepository.class);
        var tickets = mock(RedmineTicketReader.class);
        var applicabilityAi = mock(ApplicabilityAiPort.class);
        var ticketAi = mock(TicketEvidenceAiPort.class);
        var checks = mock(ProgrammedEvidenceCheckService.class);
        var run = mock(VerificationRunSnapshot.class);
        when(run.status()).thenReturn("COMPLETED");
        when(run.redmineProjectId()).thenReturn("project-1");
        when(run.ticketSelections()).thenReturn(new ValidatedTicketSelections(List.of()));
        when(run.serviceTypeSnapshot()).thenReturn(ServiceType.SOFTWARE_FACTORY);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        var humanEvent = new HumanDecisionEvent(UUID.randomUUID(), HumanDecisionType.RESULT_STATUS,
                null, ResultStatus.DOES_NOT_COMPLY, "Revisión humana", actor,
                Instant.parse("2026-10-26T10:00:00Z"));
        var current = new VerificationResult(resultId, runId, expectationId, macroId, null,
                "Ticket actualizado", EvidenceType.TICKET, EvidenceLocation.INPUT, "42",
                ApplicabilityAssessment.deterministic(ApplicabilityDecision.APPLIES, "Siempre aplica"),
                ResultStatus.REVIEW_REQUIRED, ResultStatus.DOES_NOT_COMPLY,
                "No había prueba de actualización", List.of(), List.of(), List.of(humanEvent), 2);
        when(results.findByRunAndId(runId, resultId)).thenReturn(Optional.of(current));
        var expectation = new VerificationContext.ExpectationContext(expectationId, 1, macroId, null,
                EvidenceLocation.INPUT, "Ticket actualizado", ApplicabilityMode.ALWAYS,
                Cardinality.ONE, new VerificationContext.RegistryContext(UUID.randomUUID(), "ticket",
                "Ticket actualizado", EvidenceType.TICKET, null), null);
        when(loader.load(run)).thenReturn(new VerificationRunWorkLoader.Work(
                new VerificationContext.ServiceContext(UUID.randomUUID(), ServiceType.SOFTWARE_FACTORY, 1,
                        VerificationContext.Source.missing(), VerificationContext.Source.configured("project-1", "Redmine")),
                List.of(), List.of(new VerificationRunWorkLoader.WorkItem(expectation, List.of("Actualizar ticket")))));
        var ticket = new TicketSnapshot("42", "project-1", "Redmine", "Ticket observado",
                "In Progress", "2", "In Progress", false, "Descripción", null,
                Instant.parse("2026-10-26T11:00:00Z"), Instant.parse("2026-10-25T11:00:00Z"),
                List.of(new TicketSnapshot.CustomField("9", "Validación", "sí")), List.of());
        when(tickets.getTicket("project-1", "42")).thenReturn(ticket);
        when(ticketAi.assess(anyString(), anyString(), nullable(String.class), anyList(), anyList(), eq(ticket)))
                .thenReturn(new TicketEvidenceAiPort.Assessment(ResultStatus.COMPLIES,
                        "Campo actualizado", List.of(), List.of("customFields"), "gemini-test", "response-1"));
        when(retries.save(any(), any(), anyList(), anyList())).thenReturn(current);
        var service = new VerificationAiRetryService(runs, loader, results, retries, tickets,
                applicabilityAi, ticketAi, checks, Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC),
                "GEMINI", "test-key");

        service.retry(runId, resultId, 2, actor);

        ArgumentCaptor<AiRetryAttempt> attempt = ArgumentCaptor.forClass(AiRetryAttempt.class);
        verify(retries).save(attempt.capture(), any(), anyList(), anyList());
        assertThat(attempt.getValue().ticketSnapshotJson()).contains("Validación", "Ticket observado");
        assertThat(attempt.getValue().previousResultJson()).contains("No había prueba de actualización");
        assertThat(attempt.getValue().proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(attempt.getValue().effectiveStatus()).isEqualTo(ResultStatus.DOES_NOT_COMPLY);
        assertThat(attempt.getValue().outcome()).isEqualTo("HUMAN_PRESERVED");
        verify(tickets).getTicket("project-1", "42");
    }
}
