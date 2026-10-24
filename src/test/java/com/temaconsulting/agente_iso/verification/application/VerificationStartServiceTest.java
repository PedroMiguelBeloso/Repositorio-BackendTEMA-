package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.TicketSelectionInput;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.port.VerificationScopeResolver;
import com.temaconsulting.agente_iso.verification.application.error.VerificationStartFailure;

import com.temaconsulting.agente_iso.integration.application.port.RedmineTicketReader;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import com.temaconsulting.agente_iso.service.application.port.ServiceSnapshotReader;
import com.temaconsulting.agente_iso.verification.domain.PreviewExpectation;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationStartServiceTest {
    private final VerificationScopeResolver scopes = mock(VerificationScopeResolver.class);
    private final ServiceSnapshotReader services = mock(ServiceSnapshotReader.class);
    private final VerificationRunRepository runs = mock(VerificationRunRepository.class);

    @Test
    void rejectsAStalePreviewRevisionBeforeRedmineLookupOrPersistence() {
        VerificationStartService starts = new VerificationStartService(
                scopes, services, new TicketSelectionValidator(mock(RedmineTicketReader.class)), runs
        );
        VerificationTarget target = target();
        when(scopes.resolve(target)).thenReturn(preview(9));

        assertThatThrownBy(() -> starts.start(target, 8, UUID.randomUUID(), List.of()))
                .isInstanceOf(VerificationStartFailure.class)
                .hasMessageContaining("request a new preview");

        verify(services, never()).get(any());
        verify(runs, never()).create(any());
    }

    @Test
    void doesNotCreateARunWhenRedmineCannotValidateASelectedTicket() {
        UUID serviceId = UUID.randomUUID();
        VerificationTarget target = new VerificationTarget(
                serviceId, UUID.randomUUID(), VerificationScope.PROCESS, null, null
        );
        TicketSelectionGroup group = group();
        when(scopes.resolve(target)).thenReturn(preview(9, group));
        when(services.get(serviceId)).thenReturn(new ServiceSnapshotReader.ServiceSnapshot(
                serviceId, 9, List.of(), "project-42"
        ));
        var unavailable = new com.temaconsulting.agente_iso.shared.application.error.ApiException(
                com.temaconsulting.agente_iso.shared.application.error.ProblemCode.SOURCE_UNAVAILABLE,
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Redmine unavailable", List.of()
        );
        RedmineTicketReader reader = (projectId, ticketId) -> {
            throw unavailable;
        };
        VerificationStartService starts = new VerificationStartService(
                scopes, services, new TicketSelectionValidator(reader), runs
        );

        assertThatThrownBy(() -> starts.start(
                target, 9, UUID.randomUUID(), List.of(new TicketSelectionInput(group.id(), List.of("204")))
        )).isSameAs(unavailable);

        verify(runs, never()).create(any());
    }

    private VerificationTarget target() {
        return new VerificationTarget(UUID.randomUUID(), UUID.randomUUID(), VerificationScope.PROCESS, null, null);
    }

    private static TicketSelectionGroup group() {
        UUID macroId = UUID.randomUUID();
        UUID expectationId = UUID.randomUUID();
        return TicketSelectionGroup.forMacroActivity(
                macroId, "Tickets", Cardinality.ONE, List.of(expectationId), List.of(expectationId), true
        );
    }

    private static VerificationPreview preview(int revision, TicketSelectionGroup... groups) {
        List<PreviewExpectation> expectations =
                java.util.Arrays.stream(groups)
                        .flatMap(group -> group.relatedExpectationIds().stream().map(expectationId ->
                                new PreviewExpectation(
                                        expectationId, group.macroActivityId(), null,
                                        EvidenceLocation.INPUT, "Ticket", EvidenceType.TICKET,
                                        ApplicabilityMode.ALWAYS,
                                        group.cardinality(), group.id()
                                )))
                        .toList();
        return new VerificationPreview(
                UUID.randomUUID(), revision, UUID.randomUUID(), VerificationScope.PROCESS,
                null, expectations, List.of(groups)
        );
    }
}
