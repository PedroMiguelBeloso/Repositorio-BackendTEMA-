package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.application.port.DriveEvidenceSearch;
import com.temaconsulting.agente_iso.integration.application.port.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.application.port.RedmineProjectCatalog;
import com.temaconsulting.agente_iso.integration.domain.ExternalProject;
import com.temaconsulting.agente_iso.verification.application.port.TicketEvidenceAiPort;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.ExpectationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.ServiceContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.SourceStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.text.Normalizer;
import java.util.Locale;

/** Evaluates one applicable expectation, or one explicitly selected ticket for that expectation. */
@Service
public class ProgrammedEvidenceCheckService {
    private static final Logger log = LoggerFactory.getLogger(ProgrammedEvidenceCheckService.class);
    private final DriveDocumentaryAssessmentService driveAssessment;
    private final RedmineTicketReader tickets;
    private final RedmineProjectCatalog projects;
    private final TicketEvidenceAiPort ticketAi;
    private final Clock clock;

    public ProgrammedEvidenceCheckService(
            DriveDocumentaryAssessmentService driveAssessment,
            RedmineTicketReader tickets,
            Clock clock
    ) {
        this(driveAssessment, tickets, null, null, clock);
    }

    @Autowired
    public ProgrammedEvidenceCheckService(
            DriveDocumentaryAssessmentService driveAssessment,
            RedmineTicketReader tickets,
            RedmineProjectCatalog projects,
            TicketEvidenceAiPort ticketAi,
            Clock clock
    ) {
        this.driveAssessment = Objects.requireNonNull(driveAssessment);
        this.tickets = Objects.requireNonNull(tickets);
        this.projects = projects;
        this.ticketAi = ticketAi;
        this.clock = Objects.requireNonNull(clock);
    }

    public Assessment assess(
            ExpectationContext expectation,
            ServiceContext service,
            ApplicabilityDecision applicability,
            String selectedTicketId
    ) {
        return assess(expectation, service, applicability, selectedTicketId, List.of(), List.of());
    }

    public Assessment assess(ExpectationContext expectation, ServiceContext service,
                             ApplicabilityDecision applicability, String selectedTicketId,
                             List<String> policies, List<String> instructions) {
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
            case TICKET -> assessTicket(expectation, service, selectedTicketId, policies, instructions);
            case TOOL_RECORD -> assessToolRecord(expectation, service);
            case IMPUTATIONS -> unsupportedSource(expectation);
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
            String selectedTicketId,
            List<String> policies,
            List<String> instructions
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
                    List.of(reference), ticket, observedAt);
        }

        String rule = normalize(expectation.displayName() + " " + expectation.registry().canonicalName());
        if ((rule.contains("ticket existente") || rule.contains("existencia del ticket"))
                && !rule.contains("actualiz") && !rule.contains("cerrad") && !rule.contains("valid")) {
            return result(expectation, selectedTicketId, ResultStatus.COMPLIES,
                    "El ticket #" + selectedTicketId + " existe en el proyecto Redmine vinculado al servicio.",
                    List.of("Se comprobó existencia e identidad; no se evaluó contenido ni estado."),
                    List.of(reference), ticket, observedAt);
        }
        if (ticketAi != null) {
            TicketEvidenceAiPort.Assessment proposal = ticketAi.assess(expectation.displayName(),
                    expectation.registry().canonicalName(), expectation.registry().officialNomenclature(),
                    policies, instructions, ticket);
            ResultStatus status = proposal.status();
            if (status == ResultStatus.COMPLIES && rule.contains("actualiz")
                    && proposal.citedFields().stream().noneMatch(field ->
                    field.equals("journals") || field.equals("customFields"))) {
                status = ResultStatus.REVIEW_REQUIRED;
            }
            if (status == ResultStatus.COMPLIES && rule.contains("valid")
                    && proposal.citedFields().stream().noneMatch(field ->
                    field.equals("journals") || field.equals("customFields") || field.equals("description"))) {
                status = ResultStatus.REVIEW_REQUIRED;
            }
            // A closed requirement cannot be satisfied by a ticket explicitly reported open.
            if (rule.contains("cerrad") && Boolean.FALSE.equals(ticket.closed())) {
                status = ResultStatus.DOES_NOT_COMPLY;
            }
            log.info("ticket_assessed ticketId={} expectationId={} proposedStatus={} model={} responseId={}",
                    selectedTicketId, expectation.expectationId(), status, proposal.model(), proposal.responseId());
            String provenance = proposal.model() == null ? "" : " Modelo: " + proposal.model()
                    + (proposal.responseId() == null ? "." : "; respuesta: " + proposal.responseId() + ".");
            return result(expectation, selectedTicketId, status,
                    "Propuesta IA: " + proposal.rationale() + " Campos citados: " + proposal.citedFields()
                            + "." + provenance,
                    proposal.limitations(), List.of(reference), ticket, observedAt);
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
                List.of(reference), ticket, observedAt);
    }

    private Assessment assessToolRecord(ExpectationContext expectation, ServiceContext service) {
        String name = normalize(expectation.displayName() + " " + expectation.registry().canonicalName()
                + " " + expectation.registry().key());
        if (!name.contains("servicio en herramienta de tracking")
                && !name.contains("servicio en redmine")) return unsupportedSource(expectation);
        var source = service.redmineProject();
        if (source.status() != SourceStatus.CONFIGURED) {
            return result(expectation, null, ResultStatus.REVIEW_REQUIRED,
                    "El servicio no tiene un proyecto Redmine vinculado.",
                    List.of("No se puede comprobar la existencia del proyecto."), List.of());
        }
        if (projects == null) return unsupportedSource(expectation);
        try {
            ExternalProject project = projects.findById(source.id());
            if (project == null) {
                return result(expectation, null, ResultStatus.DOES_NOT_COMPLY,
                        "El proyecto Redmine vinculado al servicio no existe.", List.of(), List.of());
            }
            if (!source.id().equals(project.id())) {
                return result(expectation, null, ResultStatus.REVIEW_REQUIRED,
                        "Redmine no confirmó la identidad del proyecto vinculado.",
                        List.of("La respuesta no coincide con el proyecto configurado."), List.of());
            }
            var reference = new EvidenceReference(EvidenceType.TOOL_RECORD, IntegrationSource.REDMINE,
                    project.id(), project.name(), null, clock.instant(), null, null);
            log.info("tool_record_checked expectationId={} projectId={} status=COMPLIES",
                    expectation.expectationId(), project.id());
            return result(expectation, null, ResultStatus.COMPLIES,
                    "Se comprobó mediante lectura que existe el proyecto Redmine vinculado al servicio.",
                    List.of("No se evaluaron permisos sobre tickets individuales ni configuración del proyecto."),
                    List.of(reference));
        } catch (ApiException ex) {
            log.warn("tool_record_check_failed expectationId={} projectId={} code={}",
                    expectation.expectationId(), source.id(), ex.code());
            return result(expectation, null, ResultStatus.REVIEW_REQUIRED,
                    "No se pudo confirmar la existencia del proyecto Redmine vinculado.",
                    List.of("La consulta a Redmine falló: " + ex.code()), List.of());
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private Assessment unsupportedSource(ExpectationContext expectation) {
        String sourceName = expectation.registry().evidenceType() == EvidenceType.IMPUTATIONS
                ? "las imputaciones de la herramienta de gestión de horas"
                : "este registro de herramienta";
        return result(expectation, null, ResultStatus.REVIEW_REQUIRED,
                "No se puede comprobar automáticamente " + sourceName
                        + " porque todavía no existe una conexión de lectura. Requiere revisión humana.",
                List.of("No se consultó la herramienta externa. No se asumió ausencia ni cumplimiento."),
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
        return result(expectation, selectedTicketId, status, rationale, limitations, references, null, null);
    }

    private Assessment result(
            ExpectationContext expectation, String selectedTicketId, ResultStatus status,
            String rationale, List<String> limitations, List<EvidenceReference> references,
            TicketSnapshot observedTicket, Instant observedAt
    ) {
        String subjectTicketId = expectation.registry().evidenceType() == EvidenceType.TICKET
                ? selectedTicketId : null;
        return new Assessment(expectation.expectationId(), subjectTicketId, status, rationale,
                limitations, references, observedTicket, observedAt);
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
            List<EvidenceReference> evidenceReferences,
            TicketSnapshot observedTicket,
            Instant observedAt
    ) {
        public Assessment(UUID expectationId, String subjectTicketId, ResultStatus proposedStatus,
                          String rationale, List<String> limitations, List<EvidenceReference> evidenceReferences) {
            this(expectationId, subjectTicketId, proposedStatus, rationale, limitations, evidenceReferences,
                    null, null);
        }

        public Assessment {
            Objects.requireNonNull(expectationId);
            Objects.requireNonNull(proposedStatus);
            Objects.requireNonNull(rationale);
            limitations = List.copyOf(limitations);
            evidenceReferences = List.copyOf(evidenceReferences);
            if ((observedTicket == null) != (observedAt == null)) {
                throw new IllegalArgumentException("Ticket observation and timestamp must be provided together");
            }
        }
    }
}
