package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.application.RegistryDefinitionRepository;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.process.application.ProcessDefinitionReader;
import com.temaconsulting.agente_iso.process.application.ProcessVersionNotFoundException;
import com.temaconsulting.agente_iso.process.application.ProcessVersionNotPublishedException;
import com.temaconsulting.agente_iso.process.application.PublishedProcessReader;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.service.application.ServiceSnapshotReader;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionPlanner;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionRuleViolation;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.ExpectationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.MacroActivityContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.RegistryContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext.Source;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VerificationContextLoader {
    private final ServiceSnapshotReader services;
    private final PublishedProcessReader processes;
    private final ProcessDefinitionReader definitions;
    private final RegistryDefinitionRepository registry;
    private final TicketSelectionPlanner ticketSelectionPlanner;

    public VerificationContextLoader(
            ServiceSnapshotReader services,
            PublishedProcessReader processes,
            ProcessDefinitionReader definitions,
            RegistryDefinitionRepository registry,
            TicketSelectionPlanner ticketSelectionPlanner
    ) {
        this.services = services;
        this.processes = processes;
        this.definitions = definitions;
        this.registry = registry;
        this.ticketSelectionPlanner = ticketSelectionPlanner;
    }

    public VerificationContext load(
            UUID serviceId,
            UUID processVersionId,
            com.temaconsulting.agente_iso.verification.domain.VerificationScope scope,
            UUID macroActivityId
    ) {
        return load(new VerificationTarget(serviceId, processVersionId, scope, macroActivityId, null));
    }

    public VerificationContext load(VerificationTarget target) {
        ServiceSnapshotReader.ServiceSnapshot service = services.find(target.serviceId())
                .orElseThrow(() -> VerificationErrors.serviceNotFound(target.serviceId()));
        ProcessVersion version = readPublishedVersion(target.processVersionId());
        ProcessDefinition process = definitions.find(version.processId())
                .orElseThrow(() -> VerificationErrors.inconsistent(
                        "processVersionId",
                        "Published process version " + version.id()
                                + " references missing process " + version.processId()
                ));

        validateAssignment(service, version);
        validateM2(version, process);

        List<MacroActivity> macroActivities = selectMacroActivities(version, target);
        Map<UUID, RegistryDefinition> registryDefinitions = loadRegistryDefinitions(macroActivities);
        List<TicketSelectionGroup> groups = new ArrayList<>();
        Map<UUID, String> ticketGroupByExpectation = new LinkedHashMap<>();

        for (MacroActivity macroActivity : macroActivities) {
            TicketSelectionGroup group = planTicketSelection(macroActivity);
            if (group != null) {
                groups.add(group);
                for (UUID expectationId : group.relatedExpectationIds()) {
                    ticketGroupByExpectation.put(expectationId, group.id());
                }
            }
        }

        List<ExpectationContext> expectations = new ArrayList<>();
        for (MacroActivity macroActivity : macroActivities) {
            for (EvidenceExpectation expectation : macroActivity.expectationsInVerificationOrder()) {
                RegistryDefinition definition = registryDefinitions.get(expectation.registryDefinitionId());
                validateRegistryDefinition(expectation, definition);
                expectations.add(new ExpectationContext(
                        expectation.id(),
                        expectation.order(),
                        expectation.macroActivityId(),
                        expectation.activityId(),
                        expectation.location(),
                        expectation.displayName(),
                        expectation.applicability(),
                        expectation.cardinality(),
                        toRegistryContext(definition),
                        ticketGroupByExpectation.get(expectation.id())
                ));
            }
        }

        return new VerificationContext(
                new VerificationContext.ServiceContext(
                        service.serviceId(),
                        service.serviceType(),
                        service.revision(),
                        toSource(service.driveFolder()),
                        toSource(service.redmineProject())
                ),
                new VerificationContext.ProcessContext(
                        version.id(),
                        process.id(),
                        process.code(),
                        process.name(),
                        version.officialVersion(),
                        version.status(),
                        process.m2Enabled()
                ),
                target.scope(),
                target.macroActivityId(),
                macroActivities.stream().map(this::toMacroActivityContext).toList(),
                expectations,
                groups
        );
    }

    private ProcessVersion readPublishedVersion(UUID processVersionId) {
        try {
            return processes.getPublished(processVersionId);
        } catch (ProcessVersionNotFoundException ex) {
            throw VerificationErrors.processVersionNotFound(processVersionId);
        } catch (ProcessVersionNotPublishedException ex) {
            throw VerificationErrors.processVersionNotPublished(processVersionId);
        }
    }

    private void validateAssignment(
            ServiceSnapshotReader.ServiceSnapshot service,
            ProcessVersion version
    ) {
        boolean assigned = service.processAssignments().stream()
                .anyMatch(assignment -> assignment.processVersionId().equals(version.id())
                        && assignment.processId().equals(version.processId()));
        if (!assigned) {
            throw VerificationErrors.processVersionNotAssigned(version.id(), service.serviceId());
        }
    }

    private void validateM2(ProcessVersion version, ProcessDefinition process) {
        if (version.status() != ProcessStatus.PUBLISHED
                || !process.m2Enabled()
                || !process.code().isM2Enabled()) {
            throw VerificationErrors.processNotEnabledForM2(version.id());
        }
    }

    private List<MacroActivity> selectMacroActivities(ProcessVersion version, VerificationTarget target) {
        if (target.scope() == com.temaconsulting.agente_iso.verification.domain.VerificationScope.PROCESS) {
            return version.macroActivities();
        }
        return version.macroActivities().stream()
                .filter(macroActivity -> macroActivity.id().equals(target.macroActivityId()))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> VerificationErrors.macroActivityNotInVersion(
                        target.macroActivityId(),
                        version.id()
                ));
    }

    private Map<UUID, RegistryDefinition> loadRegistryDefinitions(List<MacroActivity> macroActivities) {
        List<UUID> registryIds = macroActivities.stream()
                .flatMap(macro -> macro.expectationsInVerificationOrder().stream())
                .map(EvidenceExpectation::registryDefinitionId)
                .distinct()
                .toList();
        return registry.findAllByIds(registryIds).stream()
                .collect(Collectors.toMap(RegistryDefinition::id, definition -> definition));
    }

    private void validateRegistryDefinition(EvidenceExpectation expectation, RegistryDefinition definition) {
        if (definition == null) {
            throw VerificationErrors.registryDefinitionNotFound(expectation.id(), expectation.registryDefinitionId());
        }
        if (expectation.evidenceType() == null
                || !expectation.evidenceType().name().equals(definition.evidenceType().name())) {
            throw VerificationErrors.inconsistent(
                    "expectationId",
                    "Expectation " + expectation.id() + " has evidence type "
                            + expectation.evidenceType() + " but registry definition "
                            + definition.id() + " has " + definition.evidenceType()
            );
        }
        if (!java.util.Objects.equals(expectation.officialNomenclature(), definition.officialNomenclature())) {
            throw VerificationErrors.inconsistent(
                    "expectationId",
                    "Expectation " + expectation.id() + " has nomenclature inconsistent with registry definition "
                            + definition.id()
            );
        }
        if (definition.requiresNomenclature()
                && (definition.officialNomenclature() == null || definition.officialNomenclature().isBlank())) {
            throw VerificationErrors.inconsistent(
                    "expectationId",
                    "Expectation " + expectation.id() + " references published "
                            + definition.evidenceType() + " registry definition " + definition.id()
                            + " without official nomenclature"
            );
        }
    }

    private TicketSelectionGroup planTicketSelection(MacroActivity macroActivity) {
        try {
            return ticketSelectionPlanner.plan(macroActivity).orElse(null);
        } catch (TicketSelectionRuleViolation ex) {
            throw VerificationErrors.ticketSelectionRule("macroActivityId", ex.getMessage());
        }
    }

    private RegistryContext toRegistryContext(RegistryDefinition definition) {
        return new RegistryContext(
                definition.id(),
                definition.key(),
                definition.canonicalName(),
                definition.evidenceType(),
                definition.officialNomenclature()
        );
    }

    private MacroActivityContext toMacroActivityContext(MacroActivity macroActivity) {
        return new MacroActivityContext(
                macroActivity.id(),
                macroActivity.name(),
                macroActivity.order(),
                macroActivity.activities().stream()
                        .map(activity -> new VerificationContext.ActivityContext(
                                activity.id(),
                                activity.name(),
                                activity.order()
                        ))
                        .toList()
        );
    }

    private Source toSource(ExternalSource source) {
        return source == null ? Source.missing() : Source.configured(source.id(), source.name());
    }
}
