package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.TicketSelectionInput;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunDraft;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.port.VerificationScopeResolver;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import com.temaconsulting.agente_iso.service.application.port.ServiceSnapshotReader;
import com.temaconsulting.agente_iso.verification.domain.PreviewExpectation;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelection;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationStartServiceTotalTest {
    @Test
    void twoDocumentsAndOneExpectationForTwoTicketsProduceFourUnits() {
        var scopes = mock(VerificationScopeResolver.class);
        var services = mock(ServiceSnapshotReader.class);
        var validator = mock(TicketSelectionValidator.class);
        var runs = mock(VerificationRunRepository.class);
        when(runs.create(any())).thenReturn(mock(com.temaconsulting.agente_iso.verification.application.model.CreatedVerificationRun.class));
        var start = new VerificationStartService(scopes, services, validator, runs);
        UUID serviceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        UUID ticketExpectationId = UUID.randomUUID();
        String groupId = TicketSelectionGroup.stableIdFor(macroId);
        VerificationTarget target = new VerificationTarget(serviceId, versionId, VerificationScope.PROCESS, null, null);
        var ticket = new PreviewExpectation(ticketExpectationId, macroId, null, EvidenceLocation.INPUT,
                "Ticket actualizado", EvidenceType.TICKET, ApplicabilityMode.ALWAYS,
                Cardinality.MULTIPLE, groupId);
        var document1 = new PreviewExpectation(UUID.randomUUID(), macroId, null, EvidenceLocation.INPUT,
                "Highlights", EvidenceType.DOCUMENT, ApplicabilityMode.ALWAYS, Cardinality.ONE, null);
        var document2 = new PreviewExpectation(UUID.randomUUID(), macroId, null, EvidenceLocation.OUTPUT,
                "Planilla", EvidenceType.DOCUMENT, ApplicabilityMode.CASE_DEPENDENT, Cardinality.ONE, null);
        when(scopes.resolve(target)).thenReturn(new VerificationPreview(serviceId, 2, versionId,
                VerificationScope.PROCESS, null, List.of(document1, ticket, document2),
                List.of(TicketSelectionGroup.forMacroActivity(macroId, "Tickets", Cardinality.MULTIPLE,
                        List.of(ticketExpectationId), List.of(ticketExpectationId), true))));
        when(services.get(serviceId)).thenReturn(new ServiceSnapshotReader.ServiceSnapshot(
                serviceId, 2, List.of(), "redmine"));
        when(validator.validate(any(), any(), any())).thenReturn(new ValidatedTicketSelections(List.of(
                new ValidatedTicketSelection(groupId, macroId, List.of("204", "205")))));

        start.start(target, 2, UUID.randomUUID(), List.of(new TicketSelectionInput(groupId, List.of("204", "205"))));

        ArgumentCaptor<VerificationRunDraft> draft = ArgumentCaptor.forClass(VerificationRunDraft.class);
        verify(runs).create(draft.capture());
        assertThat(draft.getValue().total()).isEqualTo(4);
    }
}
