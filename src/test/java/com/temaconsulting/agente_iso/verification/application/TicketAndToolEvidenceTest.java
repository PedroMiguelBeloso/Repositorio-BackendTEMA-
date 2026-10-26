package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.application.port.RedmineProjectCatalog;
import com.temaconsulting.agente_iso.integration.application.port.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.domain.ExternalProject;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.application.port.TicketEvidenceAiPort;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TicketAndToolEvidenceTest {
    private final RedmineProjectCatalog projects = mock(RedmineProjectCatalog.class);
    private final RedmineTicketReader tickets = mock(RedmineTicketReader.class);
    private final TicketEvidenceAiPort ai = mock(TicketEvidenceAiPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
    private final ProgrammedEvidenceCheckService checks = new ProgrammedEvidenceCheckService(
            mock(DriveDocumentaryAssessmentService.class), tickets, projects, ai, clock);

    @Test
    void trackingServiceChecksTheLinkedRedmineProjectAndKeepsAReference() {
        when(projects.findById("9")).thenReturn(new ExternalProject("9", "Servicio TEMA", "tema"));
        var result = checks.assess(expectation(EvidenceType.TOOL_RECORD,
                        "Servicio en herramienta de tracking del servicio"), service(),
                ApplicabilityDecision.APPLIES, null);
        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(result.evidenceReferences()).singleElement().satisfies(ref -> {
            assertThat(ref.externalId()).isEqualTo("9");
            assertThat(ref.displayName()).isEqualTo("Servicio TEMA");
        });
        verifyNoInteractions(tickets, ai);
    }

    @Test
    void genericToolRecordDoesNotClaimTheRedmineProjectProvesIt() {
        var result = checks.assess(expectation(EvidenceType.TOOL_RECORD, "Otro registro de herramienta"),
                service(), ApplicabilityDecision.APPLIES, null);
        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);
        verifyNoInteractions(projects, tickets, ai);
    }

    @Test
    void missingLinkedProjectIsAConfirmedAbsence() {
        var result = checks.assess(expectation(EvidenceType.TOOL_RECORD,
                        "Servicio en herramienta de tracking del servicio"), service(),
                ApplicabilityDecision.APPLIES, null);
        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.DOES_NOT_COMPLY);
        assertThat(result.evidenceReferences()).isEmpty();
    }

    @Test
    void existingTicketIsDeterministicButUpdatedTicketNeedsRelevantFieldEvidence() {
        TicketSnapshot ticket = ticket(false);
        when(tickets.getTicket("9", "42")).thenReturn(ticket);
        var existing = checks.assess(expectation(EvidenceType.TICKET, "Ticket existente"),
                service(), ApplicabilityDecision.APPLIES, "42");
        assertThat(existing.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        verifyNoInteractions(ai);

        when(ai.assess(eq("Ticket actualizado"), any(), nullable(String.class), any(), any(), eq(ticket)))
                .thenReturn(new TicketEvidenceAiPort.Assessment(ResultStatus.COMPLIES,
                        "La fecha indica un cambio", List.of(), List.of("updatedAt"), "gemini", "r1"));
        var unproven = checks.assess(expectation(EvidenceType.TICKET, "Ticket actualizado"),
                service(), ApplicabilityDecision.APPLIES, "42");
        assertThat(unproven.proposedStatus()).isEqualTo(ResultStatus.REVIEW_REQUIRED);

        when(ai.assess(eq("Ticket actualizado"), any(), nullable(String.class), any(), any(), eq(ticket)))
                .thenReturn(new TicketEvidenceAiPort.Assessment(ResultStatus.COMPLIES,
                        "El historial muestra un cambio del campo solución", List.of(),
                        List.of("journals"), "gemini", "r2"));
        var proven = checks.assess(expectation(EvidenceType.TICKET, "Ticket actualizado"),
                service(), ApplicabilityDecision.APPLIES, "42");
        assertThat(proven.proposedStatus()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(proven.rationale()).contains("r2");
    }

    @Test
    void anOpenTicketCannotSatisfyAClosedTicketRequirement() {
        TicketSnapshot ticket = ticket(false);
        when(tickets.getTicket("9", "42")).thenReturn(ticket);
        when(ai.assess(any(), any(), nullable(String.class), any(), any(), any()))
                .thenReturn(new TicketEvidenceAiPort.Assessment(ResultStatus.COMPLIES,
                        "Respuesta contradictoria", List.of(), List.of("closed"), "gemini", "r3"));
        var result = checks.assess(expectation(EvidenceType.TICKET, "Ticket cerrado con solución validada"),
                service(), ApplicabilityDecision.APPLIES, "42");
        assertThat(result.proposedStatus()).isEqualTo(ResultStatus.DOES_NOT_COMPLY);
    }

    private static VerificationContext.ExpectationContext expectation(EvidenceType type, String name) {
        return new VerificationContext.ExpectationContext(UUID.randomUUID(), 1, UUID.randomUUID(), null,
                EvidenceLocation.INPUT, name, ApplicabilityMode.ALWAYS, Cardinality.ONE,
                new VerificationContext.RegistryContext(UUID.randomUUID(), "test", name, type, null), null);
    }

    private static VerificationContext.ServiceContext service() {
        return new VerificationContext.ServiceContext(UUID.randomUUID(), ServiceType.SOFTWARE_FACTORY, 1,
                VerificationContext.Source.missing(),
                VerificationContext.Source.configured("9", "Servicio TEMA"));
    }

    private static TicketSnapshot ticket(boolean closed) {
        return new TicketSnapshot("42", "9", "Servicio TEMA", "Entrega", "Open", "1", "Open",
                closed, "Se solicitó una corrección", null, Instant.parse("2026-10-25T12:00:00Z"),
                Instant.parse("2026-10-24T12:00:00Z"),
                List.of(new TicketSnapshot.CustomField("5", "Solución validada", "No")),
                List.of(new TicketSnapshot.JournalEntry("1", "Admin", "Se revisó", Instant.now(),
                        List.of(new TicketSnapshot.JournalChange("status", "New", "Open")))));
    }
}
