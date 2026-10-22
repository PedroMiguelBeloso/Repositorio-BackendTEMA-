package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;

import com.temaconsulting.agente_iso.catalog.application.port.RegistryDefinitionRepository;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.process.application.port.PublishedProcessReader;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.VerificationContext;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Rebuilds work from the immutable published version and the persisted run snapshot. */
@Service
public class VerificationRunWorkLoader {
    private final PublishedProcessReader versions;
    private final RegistryDefinitionRepository registry;

    public VerificationRunWorkLoader(PublishedProcessReader versions, RegistryDefinitionRepository registry) {
        this.versions = versions;
        this.registry = registry;
    }

    public Work load(VerificationRunSnapshot run) {
        ProcessVersion version = versions.getPublished(run.processVersionId());
        List<MacroActivity> macros = version.macroActivities().stream()
                .filter(macro -> run.scope() == VerificationScope.PROCESS || macro.id().equals(run.macroActivityId()))
                .toList();
        List<UUID> ids = macros.stream().flatMap(macro -> macro.expectationsInVerificationOrder().stream())
                .map(EvidenceExpectation::registryDefinitionId).distinct().toList();
        Map<UUID, RegistryDefinition> definitions = registry.findAllByIds(ids).stream()
                .collect(Collectors.toMap(RegistryDefinition::id, value -> value));
        List<WorkItem> items = new ArrayList<>();
        for (MacroActivity macro : macros) {
            Map<UUID, String> instructions = macro.activities().stream()
                    .collect(Collectors.toMap(Activity::id, Activity::instructions));
            for (EvidenceExpectation evidence : macro.expectationsInVerificationOrder()) {
                RegistryDefinition definition = definitions.get(evidence.registryDefinitionId());
                if (definition == null || evidence.evidenceType() == null
                        || !definition.evidenceType().name().equals(evidence.evidenceType().name())) {
                    throw new IllegalStateException("Published expectation references an inconsistent registry definition");
                }
                String displayName = evidence.displayName() == null
                        ? definition.canonicalName() : evidence.displayName();
                VerificationContext.ExpectationContext expectation = new VerificationContext.ExpectationContext(
                        evidence.id(), evidence.order(), macro.id(), evidence.activityId(), evidence.location(),
                        displayName, evidence.applicability(), evidence.cardinality(),
                        new VerificationContext.RegistryContext(definition.id(), definition.key(),
                                definition.canonicalName(), definition.evidenceType(), evidence.officialNomenclature()),
                        macro.requiresTicketSelection() && evidence.usesTicketSelectionContext()
                                ? TicketSelectionGroup.stableIdFor(macro.id()) : null
                );
                items.add(new WorkItem(expectation, evidence.activityId() == null
                        ? List.of() : List.of(instructions.get(evidence.activityId()))));
            }
        }
        List<String> policies = version.policies().stream().map(policy -> policy.text()).toList();
        VerificationContext.ServiceContext service = new VerificationContext.ServiceContext(
                run.serviceId(), run.serviceTypeSnapshot(), run.serviceRevisionSnapshot(),
                run.driveFolderId() == null ? VerificationContext.Source.missing()
                        : VerificationContext.Source.configured(run.driveFolderId(), run.driveFolderName()),
                run.redmineProjectId() == null ? VerificationContext.Source.missing()
                        : VerificationContext.Source.configured(run.redmineProjectId(), run.redmineProjectName())
        );
        return new Work(service, policies, List.copyOf(items));
    }

    public record Work(VerificationContext.ServiceContext service, List<String> policies, List<WorkItem> items) {}
    public record WorkItem(VerificationContext.ExpectationContext expectation, List<String> instructions) {}
}
