package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.application.RegistryDefinitionRepository;
import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.process.application.ProcessDefinitionReader;
import com.temaconsulting.agente_iso.process.application.ProcessVersionNotFoundException;
import com.temaconsulting.agente_iso.process.application.ProcessVersionNotPublishedException;
import com.temaconsulting.agente_iso.process.application.PublishedProcessReader;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.service.application.ServiceSnapshotReader;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import com.temaconsulting.agente_iso.verification.api.dto.VerificationPreviewResponse;
import com.temaconsulting.agente_iso.verification.api.mapper.VerificationApiMapper;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionPlanner;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.temaconsulting.agente_iso.verification.application.VerificationContextFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationContextLoaderTest {
    private final ServiceSnapshotReader services = mock(ServiceSnapshotReader.class);
    private final PublishedProcessReader processes = mock(PublishedProcessReader.class);
    private final ProcessDefinitionReader definitions = mock(ProcessDefinitionReader.class);
    private final RegistryDefinitionRepository registry = mock(RegistryDefinitionRepository.class);
    private final VerificationContextFixtures.Fixture fixture = VerificationContextFixtures.startUp();
    private VerificationContextLoader loader;

    @BeforeEach
    void setUp() {
        loader = new VerificationContextLoader(
                services,
                processes,
                definitions,
                registry,
                new TicketSelectionPlanner()
        );
        when(services.find(fixture.serviceId())).thenReturn(Optional.of(fixture.service()));
        when(processes.getPublished(fixture.versionId())).thenReturn(fixture.version());
        when(definitions.find(fixture.processId())).thenReturn(Optional.of(fixture.process()));
        when(registry.findAllByIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(fixture.registryDefinitions());
    }

    @Test
    void loadsStartupInputsActivityRecordsOutputsAndServiceProcessContext() {
        VerificationContext context = load(VerificationScope.PROCESS, null);

        assertThat(context.service().id()).isEqualTo(fixture.serviceId());
        assertThat(context.service().type()).isEqualTo(ServiceType.SUPPORT_ERP);
        assertThat(context.service().revision()).isEqualTo(4);
        assertThat(context.process().versionId()).isEqualTo(fixture.versionId());
        assertThat(context.process().code().name()).isEqualTo("START_UP");
        assertThat(context.process().name()).isEqualTo("Start Up");
        assertThat(context.process().status()).isEqualTo(ProcessStatus.PUBLISHED);
        assertThat(context.process().m2Enabled()).isTrue();
        assertThat(context.expectations()).extracting(VerificationContext.ExpectationContext::location)
                .containsExactly(
                        EvidenceLocation.INPUT,
                        EvidenceLocation.ACTIVITY_RECORD,
                        EvidenceLocation.ACTIVITY_RECORD,
                        EvidenceLocation.OUTPUT,
                        EvidenceLocation.INPUT,
                        EvidenceLocation.ACTIVITY_RECORD
                );
        assertThat(context.expectations().getFirst().displayName()).isEqualTo("Propuesta Técnica");
    }

    @Test
    void processScopeIncludesEveryMacroAndMacroActivityScopeOnlySelectedMacro() {
        VerificationContext process = load(VerificationScope.PROCESS, null);
        VerificationContext macro = load(VerificationScope.MACRO_ACTIVITY, fixture.ticketMacroId());

        assertThat(process.macroActivities()).extracting(VerificationContext.MacroActivityContext::id)
                .containsExactly(fixture.organizationMacroId(), fixture.ticketMacroId());
        assertThat(macro.macroActivities()).extracting(VerificationContext.MacroActivityContext::id)
                .containsExactly(fixture.ticketMacroId());
        assertThat(macro.expectations()).extracting(VerificationContext.ExpectationContext::expectationId)
                .containsExactly(fixture.tickets().id(), fixture.ticketContext().id());
    }

    @Test
    void sharedCatalogDefinitionDoesNotDeduplicateSeparateExpectationUses() {
        VerificationContext context = load(VerificationScope.PROCESS, null);
        List<VerificationContext.ExpectationContext> capacityUses = context.expectations().stream()
                .filter(e -> e.registry().key().equals("capacity_sheet"))
                .toList();

        assertThat(capacityUses).hasSize(2);
        assertThat(capacityUses).extracting(VerificationContext.ExpectationContext::expectationId)
                .containsExactly(fixture.reviewCapacity().id(), fixture.planningCapacity().id());
        assertThat(capacityUses).extracting(VerificationContext.ExpectationContext::activityId)
                .containsExactly(fixture.reviewActivityId(), fixture.planningActivityId());
        assertThat(capacityUses).extracting(VerificationContext.ExpectationContext::location)
                .containsOnly(EvidenceLocation.ACTIVITY_RECORD);
    }

    @Test
    void preservesApplicabilityCardinalityAndCatalogDefinitionByEvidenceType() {
        VerificationContext context = load(VerificationScope.PROCESS, null);

        VerificationContext.ExpectationContext alwaysOne = expectationContext(context, fixture.proposal().id());
        VerificationContext.ExpectationContext dependentMultiple =
                expectationContext(context, fixture.planningCapacity().id());
        assertThat(alwaysOne.applicability()).isEqualTo(ApplicabilityMode.ALWAYS);
        assertThat(alwaysOne.cardinality()).isEqualTo(Cardinality.ONE);
        assertThat(alwaysOne.registry().evidenceType()).isEqualTo(EvidenceType.DOCUMENT);
        assertThat(alwaysOne.registry().officialNomenclature()).isEqualTo("Propuesta Técnica - {Servicio} - {Fecha}");
        assertThat(dependentMultiple.applicability()).isEqualTo(ApplicabilityMode.CASE_DEPENDENT);
        assertThat(dependentMultiple.cardinality()).isEqualTo(Cardinality.MULTIPLE);

        VerificationContext.ExpectationContext folder = expectationContext(context, fixture.output().id());
        VerificationContext.ExpectationContext ticket = expectationContext(context, fixture.tickets().id());
        assertThat(folder.registry().evidenceType()).isEqualTo(EvidenceType.FOLDER);
        assertThat(folder.registry().officialNomenclature()).isEqualTo("Start Up - {Servicio} - {Fecha}");
        assertThat(ticket.registry().evidenceType()).isEqualTo(EvidenceType.TICKET);
        assertThat(ticket.registry().officialNomenclature()).isNull();
        assertThat(ticket.ticketSelectionGroupId()).isNotBlank();
        VerificationContext.ExpectationContext relatedDocument = expectationContext(context, fixture.ticketContext().id());
        assertThat(relatedDocument.registry().evidenceType()).isEqualTo(EvidenceType.DOCUMENT);
        assertThat(relatedDocument.registry().officialNomenclature()).isNotBlank();
        assertThat(relatedDocument.applicability()).isEqualTo(ApplicabilityMode.CASE_DEPENDENT);
        assertThat(relatedDocument.ticketSelectionGroupId()).isEqualTo(ticket.ticketSelectionGroupId());
        assertThat(context.ticketSelectionGroups().getFirst().inputExpectationIds())
                .containsExactly(fixture.tickets().id());
        assertThat(context.ticketSelectionGroups().getFirst().relatedExpectationIds())
                .containsExactly(fixture.tickets().id(), fixture.ticketContext().id());
    }

    @Test
    void keepsMissingDriveOrRedmineSourceExplicitWithoutFailingContextLoad() {
        ServiceSnapshotReader.ServiceSnapshot noDrive = serviceWithSources(null,
                new ExternalSource("redmine-42", "ERP"));
        when(services.find(fixture.serviceId())).thenReturn(Optional.of(noDrive));
        VerificationContext first = load(VerificationScope.PROCESS, null);
        assertThat(first.service().driveFolder().status()).isEqualTo(VerificationContext.SourceStatus.MISSING);
        assertThat(first.service().driveFolder().id()).isNull();
        assertThat(first.service().redmineProject().status()).isEqualTo(VerificationContext.SourceStatus.CONFIGURED);

        ServiceSnapshotReader.ServiceSnapshot noRedmine = serviceWithSources(
                new ExternalSource("drive-42", "ERP"), null);
        when(services.find(fixture.serviceId())).thenReturn(Optional.of(noRedmine));
        VerificationContext second = load(VerificationScope.PROCESS, null);
        assertThat(second.service().redmineProject().status()).isEqualTo(VerificationContext.SourceStatus.MISSING);
        assertThat(second.service().redmineProject().id()).isNull();
    }

    @Test
    void rejectsMissingUnpublishedOrUnassignedProcessVersions() {
        UUID missingId = UUID.randomUUID();
        doThrow(new ProcessVersionNotFoundException(missingId))
                .when(processes).getPublished(missingId);
        assertFailureCode(
                () -> loader.load(fixture.serviceId(), missingId, VerificationScope.PROCESS, null),
                ProblemCode.PROCESS_VERSION_NOT_FOUND
        );

        doThrow(new ProcessVersionNotPublishedException(fixture.versionId()))
                .when(processes).getPublished(fixture.versionId());
        assertFailureCode(
                () -> load(VerificationScope.PROCESS, null),
                ProblemCode.PROCESS_VERSION_NOT_PUBLISHED
        );

        doReturn(fixture.version()).when(processes).getPublished(fixture.versionId());
        when(services.find(fixture.serviceId())).thenReturn(Optional.of(new ServiceSnapshotReader.ServiceSnapshot(
                fixture.serviceId(),
                fixture.service().revision(),
                ServiceType.SUPPORT_ERP,
                null,
                null,
                List.of(new ServiceSnapshotReader.ProcessAssignment(
                        fixture.processId(),
                        UUID.randomUUID()
                ))
        )));
        assertFailureCode(
                () -> load(VerificationScope.PROCESS, null),
                ProblemCode.PROCESS_VERSION_NOT_ASSIGNED
        );
    }

    @Test
    void rejectsForeignMacroactivityAndServiceNotFound() {
        UUID foreignMacroId = UUID.randomUUID();
        assertFailureCode(
                () -> load(VerificationScope.MACRO_ACTIVITY, foreignMacroId),
                ProblemCode.MACRO_ACTIVITY_NOT_IN_VERSION
        );
        verify(registry, never()).findAllByIds(org.mockito.ArgumentMatchers.anyCollection());

        when(services.find(fixture.serviceId())).thenReturn(Optional.empty());
        assertFailureCode(
                () -> load(VerificationScope.PROCESS, null),
                ProblemCode.SERVICE_NOT_FOUND
        );
    }

    @Test
    void rejectsPublishedProcessNotEnabledForM2() {
        when(definitions.find(fixture.processId())).thenReturn(Optional.of(
                com.temaconsulting.agente_iso.process.domain.ProcessDefinition.create(
                        fixture.processId(),
                        com.temaconsulting.agente_iso.process.domain.ProcessCode.QUALITY_CONTINUOUS_IMPROVEMENT,
                        "Mejora continua",
                        java.time.Instant.parse("2026-10-07T12:00:00Z")
                )
        ));

        assertFailureCode(
                () -> load(VerificationScope.PROCESS, null),
                ProblemCode.PROCESS_NOT_M2_ENABLED
        );
    }

    @Test
    void rejectsMissingRegistryDefinitionAndPublishedDocumentWithoutNomenclature() {
        UUID missingRegistryId = UUID.randomUUID();
        EvidenceExpectation missingExpectation = VerificationContextFixtures.expectation(
                UUID.randomUUID(), fixture.organizationMacroId(), null, missingRegistryId, "Registro inválido",
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0,
                com.temaconsulting.agente_iso.process.domain.EvidenceType.DOCUMENT, "Document"
        );
        ProcessVersion brokenReference = version(
                fixture.versionId(),
                fixture.processId(),
                ProcessStatus.PUBLISHED,
                List.of(new MacroActivity(
                        fixture.organizationMacroId(), fixture.versionId(), "Organización interna", "", 0,
                        List.of(), List.of(missingExpectation), List.of()
                ))
        );
        when(processes.getPublished(fixture.versionId())).thenReturn(brokenReference);
        when(registry.findAllByIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());
        assertFailureCode(
                () -> load(VerificationScope.PROCESS, null),
                ProblemCode.CATALOG_DEFINITION_NOT_FOUND
        );

        RegistryDefinition unnamedDocument = new RegistryDefinition(
                UUID.randomUUID(), "unnamed", "Documento sin nombre", EvidenceType.DOCUMENT, null, true
        );
        EvidenceExpectation unnamedExpectation = VerificationContextFixtures.expectation(
                UUID.randomUUID(), fixture.organizationMacroId(), null, unnamedDocument.id(), "Documento sin nombre",
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0,
                com.temaconsulting.agente_iso.process.domain.EvidenceType.DOCUMENT, null
        );
        when(processes.getPublished(fixture.versionId())).thenReturn(version(
                fixture.versionId(),
                fixture.processId(),
                ProcessStatus.PUBLISHED,
                List.of(new MacroActivity(
                        fixture.organizationMacroId(), fixture.versionId(), "Organización interna", "", 0,
                        List.of(), List.of(unnamedExpectation), List.of()
                ))
        ));
        when(registry.findAllByIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(unnamedDocument));
        assertFailureCode(
                () -> load(VerificationScope.PROCESS, null),
                ProblemCode.PUBLISHED_CONFIGURATION_INCONSISTENT
        );
    }

    @Test
    void previewUsesTheSameExpectationIdsAsTheLoadedContextAndKeepsConfiguredOrder() {
        VerificationContext context = load(VerificationScope.PROCESS, null);
        VerificationPreviewService previewService = new VerificationPreviewService(loader);
        var preview = previewService.preview(new VerificationTarget(
                fixture.serviceId(), fixture.versionId(), VerificationScope.PROCESS, null, null
        ));
        VerificationPreviewResponse response = VerificationApiMapper.toResponse(preview);

        assertThat(response.expectations()).extracting(item -> item.id())
                .containsExactlyElementsOf(context.expectations().stream()
                        .map(VerificationContext.ExpectationContext::expectationId)
                        .toList());
        assertThat(response.expectations().get(0).id()).isEqualTo(context.expectations().get(0).expectationId());
        assertThat(context.macroActivities()).extracting(VerificationContext.MacroActivityContext::order)
                .containsExactly(0, 1);
        assertThat(context.macroActivities().get(0).activities())
                .extracting(VerificationContext.ActivityContext::order)
                .containsExactly(0, 1);
        assertThat(context.expectations()).extracting(VerificationContext.ExpectationContext::order)
                .containsExactly(0, 0, 1, 0, 0, 0);
        verify(registry, org.mockito.Mockito.times(2)).findAllByIds(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void loadsExactVersionAndReadsCatalogDefinitionsInOneBatchWithoutSourceClientsOrWrites() {
        load(VerificationScope.PROCESS, null);

        verify(processes).getPublished(fixture.versionId());
        verify(registry).findAllByIds(org.mockito.ArgumentMatchers.argThat(ids ->
                ids.size() == 4
                        && ids.containsAll(fixture.registryDefinitions().stream()
                        .map(RegistryDefinition::id)
                        .toList())
        ));
    }

    private VerificationContext load(VerificationScope scope, UUID macroActivityId) {
        return loader.load(fixture.serviceId(), fixture.versionId(), scope, macroActivityId);
    }

    private ServiceSnapshotReader.ServiceSnapshot serviceWithSources(
            ExternalSource drive,
            ExternalSource redmine
    ) {
        return new ServiceSnapshotReader.ServiceSnapshot(
                fixture.serviceId(),
                fixture.service().revision(),
                ServiceType.SUPPORT_ERP,
                drive,
                redmine,
                fixture.service().processAssignments()
        );
    }

    private VerificationContext.ExpectationContext expectationContext(VerificationContext context, UUID id) {
        return context.expectations().stream()
                .filter(value -> value.expectationId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private void assertFailureCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ProblemCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(VerificationPreviewFailure.class)
                .satisfies(error -> assertThat(((VerificationPreviewFailure) error).code()).isEqualTo(code));
    }
}
