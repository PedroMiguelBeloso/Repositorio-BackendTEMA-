package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessPolicy;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.service.application.ServiceSnapshotReader;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.service.domain.ServiceType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class VerificationContextFixtures {
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private VerificationContextFixtures() {
    }

    static Fixture startUp() {
        UUID serviceId = UUID.randomUUID();
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID ticketMacroId = UUID.randomUUID();
        UUID planningActivityId = UUID.randomUUID();
        UUID reviewActivityId = UUID.randomUUID();
        UUID ticketReviewActivityId = UUID.randomUUID();
        UUID proposalRegistryId = UUID.randomUUID();
        UUID capacityRegistryId = UUID.randomUUID();
        UUID folderRegistryId = UUID.randomUUID();
        UUID ticketRegistryId = UUID.randomUUID();

        EvidenceExpectation proposal = expectation(
                UUID.randomUUID(), organizationId, null, proposalRegistryId, "Propuesta Técnica",
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0,
                com.temaconsulting.agente_iso.process.domain.EvidenceType.DOCUMENT,
                "Propuesta Técnica - {Servicio} - {Fecha}"
        );
        EvidenceExpectation planningCapacity = expectation(
                UUID.randomUUID(), organizationId, planningActivityId, capacityRegistryId, "Planilla Capacity",
                ApplicabilityMode.CASE_DEPENDENT, Cardinality.MULTIPLE, EvidenceLocation.ACTIVITY_RECORD, 1,
                com.temaconsulting.agente_iso.process.domain.EvidenceType.DOCUMENT,
                "Capacity - {Servicio} - {Fecha}"
        );
        EvidenceExpectation reviewCapacity = expectation(
                UUID.randomUUID(), organizationId, reviewActivityId, capacityRegistryId, "Planilla Capacity",
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.ACTIVITY_RECORD, 0,
                com.temaconsulting.agente_iso.process.domain.EvidenceType.DOCUMENT,
                "Capacity - {Servicio} - {Fecha}"
        );
        EvidenceExpectation output = expectation(
                UUID.randomUUID(), organizationId, null, folderRegistryId, "Documentación de inicio",
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.OUTPUT, 0,
                com.temaconsulting.agente_iso.process.domain.EvidenceType.FOLDER,
                "Start Up - {Servicio} - {Fecha}"
        );
        EvidenceExpectation tickets = expectation(
                UUID.randomUUID(), ticketMacroId, null, ticketRegistryId, "Tickets de transición",
                ApplicabilityMode.ALWAYS, Cardinality.MULTIPLE, EvidenceLocation.INPUT, 0,
                com.temaconsulting.agente_iso.process.domain.EvidenceType.TICKET,
                null
        );
        EvidenceExpectation ticketContext = expectation(
                UUID.randomUUID(), ticketMacroId, ticketReviewActivityId, proposalRegistryId,
                "Propuesta relacionada con ticket",
                ApplicabilityMode.CASE_DEPENDENT, Cardinality.MULTIPLE, EvidenceLocation.ACTIVITY_RECORD, 0,
                com.temaconsulting.agente_iso.process.domain.EvidenceType.DOCUMENT,
                "Propuesta Técnica - {Servicio} - {Fecha}"
        );

        Activity planning = activity(planningActivityId, organizationId, "Planificación", 1, planningCapacity);
        Activity review = activity(reviewActivityId, organizationId, "Revisión", 0, reviewCapacity);
        MacroActivity organization = new MacroActivity(
                organizationId,
                versionId,
                "Organización interna",
                "Acordar la organización del servicio",
                0,
                List.of(planning, review),
                List.of(proposal),
                List.of(output)
        );
        MacroActivity ticketSelection = new MacroActivity(
                ticketMacroId,
                versionId,
                "Transición",
                "Seleccionar tickets asociados",
                1,
                List.of(activity(
                        ticketReviewActivityId,
                        ticketMacroId,
                        "Revisión de tickets",
                        0,
                        ticketContext
                )),
                List.of(tickets),
                List.of()
        );
        ProcessVersion version = version(versionId, processId, ProcessStatus.PUBLISHED, List.of(
                ticketSelection,
                organization
        ));
        ProcessDefinition process = ProcessDefinition.create(processId, ProcessCode.START_UP, "Start Up", NOW);
        ServiceSnapshotReader.ServiceSnapshot service = new ServiceSnapshotReader.ServiceSnapshot(
                serviceId,
                4,
                ServiceType.SUPPORT_ERP,
                new ExternalSource("drive-folder-123", "Start Up"),
                null,
                List.of(new ServiceSnapshotReader.ProcessAssignment(processId, versionId))
        );

        return new Fixture(
                serviceId,
                processId,
                versionId,
                organizationId,
                ticketMacroId,
                planningActivityId,
                reviewActivityId,
                ticketReviewActivityId,
                proposal,
                planningCapacity,
                reviewCapacity,
                output,
                tickets,
                ticketContext,
                service,
                process,
                version,
                List.of(
                        new RegistryDefinition(proposalRegistryId, "technical_proposal", "Propuesta Técnica",
                                EvidenceType.DOCUMENT, "Propuesta Técnica - {Servicio} - {Fecha}", true),
                        new RegistryDefinition(capacityRegistryId, "capacity_sheet", "Planilla Capacity",
                                EvidenceType.DOCUMENT, "Capacity - {Servicio} - {Fecha}", true),
                        new RegistryDefinition(folderRegistryId, "start_up_folder", "Carpeta de Inicio",
                                EvidenceType.FOLDER, "Start Up - {Servicio} - {Fecha}", true),
                        new RegistryDefinition(ticketRegistryId, "transition_tickets", "Tickets de transición",
                                EvidenceType.TICKET, null, true)
                )
        );
    }

    static ProcessVersion version(
            UUID versionId,
            UUID processId,
            ProcessStatus status,
            List<MacroActivity> macroActivities
    ) {
        return new ProcessVersion(
                versionId,
                processId,
                "1.0",
                LocalDate.of(2026, 10, 1),
                status,
                3,
                "Inicio controlado del servicio",
                "Responsable de calidad",
                List.<ProcessPolicy>of(),
                macroActivities,
                status == ProcessStatus.PUBLISHED ? NOW : null,
                NOW
        );
    }

    static EvidenceExpectation expectation(
            UUID id,
            UUID macroId,
            UUID activityId,
            UUID registryId,
            String displayName,
            ApplicabilityMode applicability,
            Cardinality cardinality,
            EvidenceLocation location,
            int order,
            com.temaconsulting.agente_iso.process.domain.EvidenceType type,
            String nomenclature
    ) {
        return new EvidenceExpectation(
                id, macroId, activityId, registryId, displayName, applicability, cardinality,
                location, order, type, nomenclature
        );
    }

    private static Activity activity(
            UUID id,
            UUID macroId,
            String name,
            int order,
            EvidenceExpectation record
    ) {
        return new Activity(id, macroId, name, "Consultor", "Revisar evidencia", "1 día", order, List.of(record));
    }

    record Fixture(
            UUID serviceId,
            UUID processId,
            UUID versionId,
            UUID organizationMacroId,
            UUID ticketMacroId,
            UUID planningActivityId,
            UUID reviewActivityId,
            UUID ticketReviewActivityId,
            EvidenceExpectation proposal,
            EvidenceExpectation planningCapacity,
            EvidenceExpectation reviewCapacity,
            EvidenceExpectation output,
            EvidenceExpectation tickets,
            EvidenceExpectation ticketContext,
            ServiceSnapshotReader.ServiceSnapshot service,
            ProcessDefinition process,
            ProcessVersion version,
            List<RegistryDefinition> registryDefinitions
    ) {
        Fixture {
            registryDefinitions = List.copyOf(registryDefinitions);
        }
    }
}
