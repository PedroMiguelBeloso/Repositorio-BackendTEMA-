package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.process.application.ProcessAssignmentInfo;
import com.temaconsulting.agente_iso.process.application.ProcessAssignmentReader;
import com.temaconsulting.agente_iso.process.application.ProcessVersionNotFoundException;
import com.temaconsulting.agente_iso.process.application.PublishedProcessReader;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.service.application.ServiceSnapshotReader;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionPlanner;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationScopeResolverTest {

    private final ServiceSnapshotReader services = mock(ServiceSnapshotReader.class);
    private final PublishedProcessReader processes = mock(PublishedProcessReader.class);
    private final ProcessAssignmentReader assignments = mock(ProcessAssignmentReader.class);
    private VerificationScopeResolver resolver;

    private UUID serviceId;
    private UUID processId;
    private UUID versionId;
    private UUID firstMacroId;
    private UUID secondMacroId;

    @BeforeEach
    void setUp() {
        resolver = new VerificationScopeResolver(
                services,
                processes,
                assignments,
                new TicketSelectionPlanner()
        );
        serviceId = UUID.randomUUID();
        processId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        firstMacroId = UUID.randomUUID();
        secondMacroId = UUID.randomUUID();

        when(services.find(serviceId)).thenReturn(Optional.of(new ServiceSnapshotReader.ServiceSnapshot(
                serviceId,
                7,
                List.of(new ServiceSnapshotReader.ProcessAssignment(processId, versionId))
        )));
        when(assignments.find(versionId)).thenReturn(Optional.of(assignment(ProcessStatus.PUBLISHED, true)));
    }

    @Test
    void processScopeIncludesAllMacrosAndKeepsLocationOrderAndMetadata() {
        EvidenceExpectation firstInput = expectation(firstMacroId, null, "Input", EvidenceType.DOCUMENT,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0);
        UUID activityId = UUID.randomUUID();
        EvidenceExpectation firstRecord = expectation(firstMacroId, activityId, "Record", EvidenceType.TICKET,
                ApplicabilityMode.CASE_DEPENDENT, Cardinality.ONE, EvidenceLocation.ACTIVITY_RECORD, 0);
        MacroActivity first = macro(
                firstMacroId,
                0,
                List.of(activity(activityId, firstMacroId, List.of(firstRecord))),
                List.of(firstInput),
                List.of()
        );
        EvidenceExpectation secondOutput = expectation(secondMacroId, null, "Output", EvidenceType.FOLDER,
                ApplicabilityMode.ALWAYS, Cardinality.MULTIPLE, EvidenceLocation.OUTPUT, 0);
        MacroActivity second = macro(secondMacroId, 1, List.of(), List.of(), List.of(secondOutput));
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(first, second)));

        var preview = resolver.resolve(processTarget());

        assertThat(preview.macroActivityId()).isNull();
        assertThat(preview.expectations()).extracting(e -> e.id())
                .containsExactly(firstInput.id(), firstRecord.id(), secondOutput.id());
        assertThat(preview.expectations()).extracting(e -> e.location())
                .containsExactly(EvidenceLocation.INPUT, EvidenceLocation.ACTIVITY_RECORD, EvidenceLocation.OUTPUT);
        assertThat(preview.expectations().get(1).activityId()).isEqualTo(activityId);
        assertThat(preview.expectations().get(1).evidenceType()).isEqualTo(EvidenceType.TICKET);
        assertThat(preview.expectations().get(1).applicability()).isEqualTo(ApplicabilityMode.CASE_DEPENDENT);
        assertThat(preview.expectations().get(1).cardinality()).isEqualTo(Cardinality.ONE);
        assertThat(preview.ticketSelectionGroups()).hasSize(1);
    }

    @Test
    void macroActivityScopeIncludesOnlySelectedMacroactivity() {
        MacroActivity first = macro(firstMacroId, 0, List.of(), List.of(
                expectation(firstMacroId, null, "First", EvidenceType.DOCUMENT,
                        ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0)
        ), List.of());
        MacroActivity second = macro(secondMacroId, 1, List.of(), List.of(
                expectation(secondMacroId, null, "Second", EvidenceType.DOCUMENT,
                        ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0)
        ), List.of());
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(first, second)));

        var preview = resolver.resolve(new VerificationTarget(
                serviceId, versionId, VerificationScope.MACRO_ACTIVITY, secondMacroId, null
        ));

        assertThat(preview.macroActivityId()).isEqualTo(secondMacroId);
        assertThat(preview.expectations()).hasSize(1);
        assertThat(preview.expectations().getFirst().macroActivityId()).isEqualTo(secondMacroId);
    }

    @Test
    void rejectsForeignMacroactivityIdWithoutReadingAnotherVersion() {
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(
                macro(firstMacroId, 0, List.of(), List.of(), List.of())
        )));
        UUID foreignVersionId = UUID.randomUUID();
        UUID foreignMacroId = UUID.randomUUID();

        assertThatThrownBy(() -> resolver.resolve(new VerificationTarget(
                serviceId, versionId, VerificationScope.MACRO_ACTIVITY, foreignMacroId, null
        )))
                .isInstanceOf(VerificationPreviewFailure.class)
                .satisfies(ex -> assertThat(((VerificationPreviewFailure) ex).kind())
                        .isEqualTo(VerificationPreviewFailure.Kind.BUSINESS_RULE))
                .hasMessageContaining("does not belong to process version");

        verify(processes, never()).getPublished(foreignVersionId);
    }

    @Test
    void rejectsUnknownServiceAsNotFoundWithoutHttpTypesInResolver() {
        UUID unknownServiceId = UUID.randomUUID();
        when(services.find(unknownServiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(new VerificationTarget(
                unknownServiceId, versionId, VerificationScope.PROCESS, null, null
        )))
                .isInstanceOf(VerificationPreviewFailure.class)
                .satisfies(ex -> assertThat(((VerificationPreviewFailure) ex).kind())
                        .isEqualTo(VerificationPreviewFailure.Kind.NOT_FOUND));
    }

    @Test
    void rejectsMissingPublishedVersion() {
        when(processes.getPublished(versionId)).thenThrow(new ProcessVersionNotFoundException(versionId));

        assertThatThrownBy(() -> resolver.resolve(processTarget()))
                .isInstanceOf(VerificationPreviewFailure.class)
                .satisfies(ex -> assertThat(((VerificationPreviewFailure) ex).kind())
                        .isEqualTo(VerificationPreviewFailure.Kind.NOT_FOUND));
    }

    @Test
    void rejectsDraftVersionAsBusinessRule() {
        when(processes.getPublished(versionId))
                .thenThrow(new com.temaconsulting.agente_iso.process.application.ProcessVersionNotPublishedException(versionId));

        assertThatThrownBy(() -> resolver.resolve(processTarget()))
                .isInstanceOf(VerificationPreviewFailure.class)
                .satisfies(ex -> assertThat(((VerificationPreviewFailure) ex).kind())
                        .isEqualTo(VerificationPreviewFailure.Kind.BUSINESS_RULE));
    }

    @Test
    void rejectsVersionNotAssignedToService() {
        when(services.find(serviceId)).thenReturn(Optional.of(new ServiceSnapshotReader.ServiceSnapshot(
                serviceId, 7, List.of()
        )));
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of()));

        assertThatThrownBy(() -> resolver.resolve(processTarget()))
                .isInstanceOf(VerificationPreviewFailure.class)
                .hasMessageContaining("is not assigned to service");
    }

    @Test
    void rejectsProcessNotEnabledForM2() {
        when(assignments.find(versionId)).thenReturn(Optional.of(assignment(ProcessStatus.PUBLISHED, false)));
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of()));

        assertThatThrownBy(() -> resolver.resolve(processTarget()))
                .isInstanceOf(VerificationPreviewFailure.class)
                .satisfies(ex -> {
                    VerificationPreviewFailure failure = (VerificationPreviewFailure) ex;
                    assertThat(failure.kind()).isEqualTo(VerificationPreviewFailure.Kind.BUSINESS_RULE);
                    assertThat(failure.code()).isEqualTo(com.temaconsulting.agente_iso.shared.application.ProblemCode.UNSUPPORTED_SCOPE);
                });
    }

    @Test
    void rejectsQualityContinuousImprovement() {
        when(assignments.find(versionId)).thenReturn(Optional.of(new ProcessAssignmentInfo(
                versionId,
                processId,
                ProcessCode.QUALITY_CONTINUOUS_IMPROVEMENT,
                "Quality and Continuous Improvement",
                "1.0",
                ProcessStatus.PUBLISHED,
                false,
                List.of()
        )));
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of()));

        assertThatThrownBy(() -> resolver.resolve(processTarget()))
                .isInstanceOf(VerificationPreviewFailure.class)
                .hasMessageContaining("not enabled for M2");
    }

    @Test
    void oneTicketGroupReusesInputRecordAndOutputAndRelatesConditionalDocument() {
        EvidenceExpectation ticketInput = expectation(firstMacroId, null, "Ticket", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0);
        UUID activityId = UUID.randomUUID();
        EvidenceExpectation ticketRecord = expectation(firstMacroId, activityId, "Ticket record", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.ACTIVITY_RECORD, 0);
        EvidenceExpectation ticketOutput = expectation(firstMacroId, null, "Ticket output", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.OUTPUT, 0);
        EvidenceExpectation conditionalDocument = expectation(firstMacroId, null, "Order", EvidenceType.DOCUMENT,
                ApplicabilityMode.CASE_DEPENDENT, Cardinality.ONE, EvidenceLocation.OUTPUT, 1);
        EvidenceExpectation unrelatedDocument = expectation(firstMacroId, null, "Always document", EvidenceType.DOCUMENT,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.OUTPUT, 2);

        MacroActivity macro = macro(
                firstMacroId,
                0,
                List.of(activity(activityId, firstMacroId, List.of(ticketRecord))),
                List.of(ticketInput),
                List.of(ticketOutput, conditionalDocument, unrelatedDocument)
        );
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(macro)));

        var preview = resolver.resolve(processTarget());
        var group = preview.ticketSelectionGroups().getFirst();

        assertThat(preview.expectations()).hasSize(5);
        assertThat(preview.ticketSelectionGroups()).hasSize(1);
        assertThat(group.id()).isEqualTo("ticket-group:" + firstMacroId);
        assertThat(group.inputExpectationIds()).containsExactly(ticketInput.id());
        assertThat(group.relatedExpectationIds()).containsExactly(
                ticketInput.id(), ticketRecord.id(), ticketOutput.id(), conditionalDocument.id());
        assertThat(preview.expectations()).extracting(e -> e.ticketSelectionGroupId())
                .containsExactly(group.id(), group.id(), group.id(), group.id(), null);
    }

    @Test
    void repeatedPreviewProducesSameGroupIdAndNoPersistence() {
        EvidenceExpectation ticket = expectation(firstMacroId, null, "Ticket", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.MULTIPLE, EvidenceLocation.INPUT, 0);
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(
                macro(firstMacroId, 0, List.of(), List.of(ticket), List.of())
        )));

        var first = resolver.resolve(processTarget());
        var second = resolver.resolve(processTarget());

        assertThat(first.ticketSelectionGroups().getFirst().id())
                .isEqualTo(second.ticketSelectionGroups().getFirst().id());
        assertThat(first.ticketSelectionGroups().getFirst().cardinality()).isEqualTo(Cardinality.MULTIPLE);
        // Resolver depends only on read boundaries; there is no run repository or write port in this use case.
        assertThat(first).isEqualTo(second);
    }

    @Test
    void macroactivityWithoutTicketsDoesNotGenerateSelector() {
        EvidenceExpectation document = expectation(firstMacroId, null, "Document", EvidenceType.DOCUMENT,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0);
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(
                macro(firstMacroId, 0, List.of(), List.of(document), List.of())
        )));

        var preview = resolver.resolve(new VerificationTarget(
                serviceId, versionId, VerificationScope.MACRO_ACTIVITY, firstMacroId, null
        ));

        assertThat(preview.ticketSelectionGroups()).isEmpty();
        assertThat(preview.expectations().getFirst().ticketSelectionGroupId()).isNull();
    }

    @Test
    void ticketRecordWithoutInputStillCreatesOneGroup() {
        UUID activityId = UUID.randomUUID();
        EvidenceExpectation record = expectation(firstMacroId, activityId, "Ticket record", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.MULTIPLE, EvidenceLocation.ACTIVITY_RECORD, 0);
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(
                macro(firstMacroId, 0,
                        List.of(activity(activityId, firstMacroId, List.of(record))),
                        List.of(), List.of())
        )));

        var group = resolver.resolve(processTarget()).ticketSelectionGroups().getFirst();
        assertThat(group.inputExpectationIds()).isEmpty();
        assertThat(group.relatedExpectationIds()).containsExactly(record.id());
        assertThat(group.cardinality()).isEqualTo(Cardinality.MULTIPLE);
    }

    @Test
    void multipleTicketMacrosProduceSeparateGroups() {
        EvidenceExpectation one = expectation(firstMacroId, null, "Ticket one", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0);
        EvidenceExpectation many = expectation(secondMacroId, null, "Tickets", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.MULTIPLE, EvidenceLocation.INPUT, 0);
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(
                macro(firstMacroId, 0, List.of(), List.of(one), List.of()),
                macro(secondMacroId, 1, List.of(), List.of(many), List.of())
        )));

        var preview = resolver.resolve(processTarget());

        assertThat(preview.ticketSelectionGroups()).extracting(g -> g.macroActivityId())
                .containsExactly(firstMacroId, secondMacroId);
        assertThat(preview.ticketSelectionGroups()).extracting(g -> g.cardinality())
                .containsExactly(Cardinality.ONE, Cardinality.MULTIPLE);
    }

    @Test
    void rejectsDifferentTicketCardinalitiesInOneMacroactivity() {
        EvidenceExpectation input = expectation(firstMacroId, null, "Ticket input", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0);
        EvidenceExpectation output = expectation(firstMacroId, null, "Ticket output", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.MULTIPLE, EvidenceLocation.OUTPUT, 0);
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(
                macro(firstMacroId, 0, List.of(), List.of(input), List.of(output))
        )));

        assertThatThrownBy(() -> resolver.resolve(processTarget()))
                .isInstanceOf(VerificationPreviewFailure.class)
                .hasMessageContaining("same cardinality");
    }

    @Test
    void rejectsMoreThanOneTicketInput() {
        EvidenceExpectation first = expectation(firstMacroId, null, "Ticket one", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 0);
        EvidenceExpectation second = expectation(firstMacroId, null, "Ticket two", EvidenceType.TICKET,
                ApplicabilityMode.ALWAYS, Cardinality.ONE, EvidenceLocation.INPUT, 1);
        when(processes.getPublished(versionId)).thenReturn(publishedVersion(List.of(
                macro(firstMacroId, 0, List.of(), List.of(first, second), List.of())
        )));

        assertThatThrownBy(() -> resolver.resolve(processTarget()))
                .isInstanceOf(VerificationPreviewFailure.class)
                .hasMessageContaining("more than one TICKET input");
    }

    private ProcessAssignmentInfo assignment(ProcessStatus status, boolean m2Enabled) {
        return new ProcessAssignmentInfo(
                versionId,
                processId,
                ProcessCode.SUPPORT_SOFTWARE_FACTORY,
                "Soporte y Software Factory",
                "2.0",
                status,
                m2Enabled,
                List.of()
        );
    }

    private VerificationTarget processTarget() {
        return new VerificationTarget(serviceId, versionId, VerificationScope.PROCESS, null, null);
    }

    private ProcessVersion publishedVersion(List<MacroActivity> macros) {
        return new ProcessVersion(
                versionId,
                processId,
                "2.0",
                LocalDate.of(2026, 10, 1),
                ProcessStatus.PUBLISHED,
                3,
                "Objective",
                "Owner",
                List.of(),
                macros,
                Instant.parse("2026-10-01T10:00:00Z"),
                Instant.parse("2026-10-01T09:00:00Z")
        );
    }

    private MacroActivity macro(
            UUID id,
            int order,
            List<Activity> activities,
            List<EvidenceExpectation> inputs,
            List<EvidenceExpectation> outputs
    ) {
        return new MacroActivity(id, versionId, "Macro " + order, null, order, activities, inputs, outputs);
    }

    private Activity activity(UUID id, UUID macroId, List<EvidenceExpectation> records) {
        return new Activity(id, macroId, "Activity", "Owner", "Instructions", null, 0, records);
    }

    private EvidenceExpectation expectation(
            UUID macroId,
            UUID activityId,
            String name,
            EvidenceType type,
            ApplicabilityMode applicability,
            Cardinality cardinality,
            EvidenceLocation location,
            int order
    ) {
        return new EvidenceExpectation(
                UUID.randomUUID(), macroId, activityId, UUID.randomUUID(), name,
                applicability, cardinality, location, order, type, null
        );
    }
}
