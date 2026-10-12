package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch;
import com.temaconsulting.agente_iso.integration.application.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.ExpectationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.ServiceContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.SourceStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Evaluates one applicable expectation, or one explicitly selected ticket for that expectation. */
@Service
public class ProgrammedEvidenceCheckService {
    private final DriveDocumentaryAssessmentService driveAssessment;
    private final RedmineTicketReader tickets;
    private final Clock clock;

    public ProgrammedEvidenceCheckService(
            DriveDocumentaryAssessmentService driveAssessment,
            RedmineTicketReader tickets,
            Clock clock
    ) {
        this.driveAssessment = Objects.requireNonNull(driveAssessment);
        this.tickets = Objects.requireNonNull(tickets);
        this.clock = Objects.requireNonNull(clock);
    }

    public Assessment assess(
            ExpectationContext expectation,
            ServiceContext service,
            ApplicabilityDecision applicability,
            String selectedTicketId
    ) {
        Objects.requireNonNull(expectation, "expectation");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(applicability, "applicability");

        if (applicability == ApplicabilityDecision.NOT_APPLICABLE) {
            return result(expectation, selectedTicketId, ResultStatus.NOT_APPLICABLE,
                    "La expectativa fue declarada no aplicable por el flujo de aplicabilidad.",
                    List.of(), List.of());
        }
        if (applicability == ApplicabilityDecision.UNDETERMINED) {
            return result(expectation, selectedTicketId, ResultStatus.REVIEW_REQUIRED,
                    "Primero falta resolver la aplicabilidad; no se comprobó la ausencia de evidencia.",
                    List.of("No se consultó ninguna fuente externa hasta resolver la aplicabilidad."),
                    List.of());
        }

        return switch (expectation.registry().evidenceType()) {
            case DOCUMENT, FOLDER -> assessDrive(expectation, service);
            case TICKET -> assessTicket(expectation, service, selectedTicketId);
            case IMPUTATIONS, TOOL_RECORD -> unsupportedSource(expectation);
        };
    }

    private Assessment assessDrive(ExpectationContext expectation, ServiceContext service) {
        var folder = service.driveFolder();
        if (folder.status() != SourceStatus.CONFIGURED) {
            return result(expectation, null, ResultStatus.REVIEW_REQUIRED,
                    "No se pudo comprobar la evidencia documental porque el servicio no tiene una carpeta Drive configurada.",
                    List.of("La fuente Google Drive no está configurada."), List.of());
        }

        DriveEvidenceSearch.Kind kind = expectation.registry().evidenceType() == EvidenceType.DOCUMENT
                ? DriveEvidenceSearch.Kind.DOCUMENT
                : DriveEvidenceSearch.Kind.FOLDER;
        DriveEvidenceSearch.Cardinality cardinality = expectation.cardinality() == Cardinality.ONE
                ? DriveEvidenceSearch.Cardinality.ONE
                : DriveEvidenceSearch.Cardinality.MULTIPLE;
        DriveEvidenceSearch.SearchRequest request = new DriveEvidenceSearch.SearchRequest(
                folder.id(),
                kind,
                expectation.registry().officialNomenclature(),
                cardinality
        );
        DriveDocumentaryAssessmentService.Assessment driveResult =
                driveAssessment.assess(request, ApplicabilityDecision.APPLIES);
        List<EvidenceReference> references = driveResult.evidence().stream()
                .map(reference -> new EvidenceReference(
                        reference.kind() == null ? null : EvidenceType.valueOf(reference.kind().name()),
                        reference.source(),
                        reference.externalId(),
                        reference.displayName(),
                        reference.webUrl(),
                        reference.observedAt(),
                        reference.relativePath(),
                        null
                ))
                .toList();
        return result(expectation, null, driveResult.proposedStatus(), driveResult.rationale(),
                driveResult.limitations(), references);
    }

    private Assessment assessTicket(
            ExpectationContext expectation,
            ServiceContext service,
            String selectedTicketId
    ) {
        if (selectedTicketId == null || selectedTicketId.isBlank()) {
            return result(expectation, selectedTicketId, ResultStatus.REVIEW_REQUIRED,
                    "No se recibió el ID del ticket seleccionado para esta expectativa.",
                    List.of("No se consultó Redmine porque no hay un ticket seleccionado."), List.of());
        }

        var project = service.redmineProject();
        if (project.status() != SourceStatus.CONFIGURED) {
            return result(expectation, selectedTicketId, ResultStatus.REVIEW_REQUIRED,
                    "No se pudo consultar el ticket seleccionado porque el servicio no tiene un proyecto Redmine configurado.",
                    List.of("La fuente Redmine no está configurada."), List.of());
        }

        TicketSnapshot ticket;
        try {
            ticket = tickets.getTicket(project.id(), selectedTicketId);
        } catch (ApiException ex) {
            return result(expectation, selectedTicketId, ResultStatus.REVIEW_REQUIRED,
                    "No se pudo consultar el ticket seleccionado en Redmine: "
                            + ex.code() + " - " + ex.safeMessage(),
                    List.of("La lectura de Redmine falló con el error " + ex.code() + "."), List.of());
        }
        if (ticket == null) {
            return result(expectation, selectedTicketId, ResultStatus.REVIEW_REQUIRED,
                    "Redmine no devolvió un snapshot para el ticket seleccionado.",
                    List.of("La respuesta de lectura del ticket fue nula."), List.of());
        }

        Instant observedAt = clock.instant();
        EvidenceReference reference = ticketReference(ticket, selectedTicketId, observedAt);
        if (!selectedTicketId.equals(ticket.id()) || !project.id().equals(ticket.projectId())) {
            return result(expectation, selectedTicketId, ResultStatus.REVIEW_REQUIRED,
                    "El snapshot de Redmine no conserva el ID del ticket seleccionado o el proyecto configurado.",
                    List.of("La identidad del ticket devuelto no coincide con la consulta; revisar la referencia."),
                    List.of(reference));
        }

        String rationale = "Se consultó el ticket #" + selectedTicketId + " del proyecto "
                + ticket.projectId() + ": estado=" + observedStatus(ticket)
                + ", closed=" + observedClosed(ticket.closed())
                + ", updatedAt=" + observedUpdatedAt(ticket.updatedAt()) + ". "
                + "updatedAt solo informa una marca temporal y no demuestra que ocurrió la actualización "
                + "requerida por la actividad. EvidenceType.TICKET no identifica un campo o valor objetivo "
                + "para comprobar la condición de esta expectativa; la existencia del ticket no implica cumplimiento. "
                + "No se interpretaron journals ni comentarios como señal de validación porque el catálogo no "
                + "identifica qué comentario, campo o cambio constituiría esa señal.";
        return result(expectation, selectedTicketId, ResultStatus.REVIEW_REQUIRED, rationale,
                List.of("No hay una regla programable que identifique el campo, valor o cambio exigido por esta expectativa."),
                List.of(reference));
    }

    private Assessment unsupportedSource(ExpectationContext expectation) {
        String sourceName = expectation.registry().evidenceType() == EvidenceType.IMPUTATIONS
                ? "IMPUTATIONS" : "TOOL_RECORD";
        return result(expectation, null, ResultStatus.REVIEW_REQUIRED,
                "No se pudo comprobar la expectativa porque la fuente " + sourceName
                        + " no tiene un lector operativo disponible.",
                List.of("La fuente " + sourceName + " no está conectada a un puerto de lectura programado."),
                List.of());
    }

    private EvidenceReference ticketReference(TicketSnapshot ticket, String selectedTicketId, Instant observedAt) {
        String ticketId = ticket.id() == null ? selectedTicketId : ticket.id();
        String displayName = ticket.subject() == null || ticket.subject().isBlank()
                ? "Ticket #" + ticketId
                : ticket.subject();
        return new EvidenceReference(EvidenceType.TICKET, IntegrationSource.REDMINE, ticketId,
                displayName, null, observedAt, null, ticketId);
    }

    private Assessment result(
            ExpectationContext expectation,
            String selectedTicketId,
            ResultStatus status,
            String rationale,
            List<String> limitations,
            List<EvidenceReference> references
    ) {
        String subjectTicketId = expectation.registry().evidenceType() == EvidenceType.TICKET
                ? selectedTicketId : null;
        return new Assessment(expectation.expectationId(), subjectTicketId, status, rationale,
                limitations, references);
    }

    private static String observedStatus(TicketSnapshot ticket) {
        String status = ticket.statusName() == null ? ticket.status() : ticket.statusName();
        return status == null ? "no informado" : status;
    }

    private static String observedClosed(Boolean closed) {
        return closed == null ? "no informado" : closed.toString();
    }

    private static String observedUpdatedAt(Instant updatedAt) {
        return updatedAt == null ? "no informado" : updatedAt.toString();
    }

    public record EvidenceReference(
            EvidenceType kind,
            IntegrationSource source,
            String externalId,
            String displayName,
            String webUrl,
            Instant observedAt,
            String relativePath,
            String ticketId
    ) {
    }

    public record Assessment(
            UUID expectationId,
            String subjectTicketId,
            ResultStatus proposedStatus,
            String rationale,
            List<String> limitations,
            List<EvidenceReference> evidenceReferences
    ) {
        public Assessment {
            Objects.requireNonNull(expectationId);
            Objects.requireNonNull(proposedStatus);
            Objects.requireNonNull(rationale);
            limitations = List.copyOf(limitations);
            evidenceReferences = List.copyOf(evidenceReferences);
        }
    }
}
