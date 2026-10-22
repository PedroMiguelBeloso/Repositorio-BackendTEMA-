package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.CreatedVerificationRun;
import com.temaconsulting.agente_iso.verification.application.model.TicketSelectionInput;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunDraft;
import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import com.temaconsulting.agente_iso.verification.application.port.VerificationScopeResolver;
import com.temaconsulting.agente_iso.verification.application.error.VerificationStartFailure;

import com.temaconsulting.agente_iso.service.application.port.ServiceSnapshotReader;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VerificationStartService {
    private final VerificationScopeResolver scopeResolver;
    private final ServiceSnapshotReader services;
    private final TicketSelectionValidator ticketSelectionValidator;
    private final VerificationRunRepository runs;

    public VerificationStartService(
            VerificationScopeResolver scopeResolver,
            ServiceSnapshotReader services,
            TicketSelectionValidator ticketSelectionValidator,
            VerificationRunRepository runs
    ) {
        this.scopeResolver = scopeResolver;
        this.services = services;
        this.ticketSelectionValidator = ticketSelectionValidator;
        this.runs = runs;
    }

    public CreatedVerificationRun start(
            VerificationTarget target,
            int serviceRevision,
            UUID startedBy,
            List<TicketSelectionInput> selections
    ) {
        VerificationPreview preview = scopeResolver.resolve(target);
        if (preview.serviceRevision() != serviceRevision) {
            throw VerificationStartFailure.conflict(
                    "serviceRevision",
                    "Service configuration changed after preview; request a new preview before starting"
            );
        }

        ServiceSnapshotReader.ServiceSnapshot service = services.get(target.serviceId());
        if (service.revision() != serviceRevision) {
            throw VerificationStartFailure.conflict(
                    "serviceRevision",
                    "Service configuration changed after preview; request a new preview before starting"
            );
        }
        ValidatedTicketSelections validated = ticketSelectionValidator.validate(
                preview, selections, service.redmineProjectId()
        );
        int total = preview.expectations().stream().mapToInt(expectation -> {
            if (!expectation.isTicket()) {
                return 1;
            }
            return validated.groups().stream()
                    .filter(group -> group.groupId().equals(expectation.ticketSelectionGroupId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Validated ticket group is missing"))
                    .ticketIds().size();
        }).reduce(0, Math::addExact);
        return runs.create(new VerificationRunDraft(
                UUID.randomUUID(),
                target.serviceId(),
                serviceRevision,
                target.processVersionId(),
                startedBy,
                target.scope(),
                target.macroActivityId(),
                target.note(),
                validated,
                total
        ));
    }
}
