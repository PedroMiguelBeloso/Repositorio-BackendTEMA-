package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class PublicationValidator {

    public List<Violation> validate(ProcessVersion version, List<RegistryDefinition> definitions) {
        List<Violation> violations = new ArrayList<>();
        Map<UUID, RegistryDefinition> registry = definitions.stream()
                .collect(Collectors.toMap(RegistryDefinition::id, Function.identity()));

        if (version.objective() == null || version.objective().isBlank()) {
            violations.add(new Violation("objective", "Objective is required before publication"));
        }
        if (version.owner() == null || version.owner().isBlank()) {
            violations.add(new Violation("owner", "Owner is required before publication"));
        }
        if (version.macroActivities().isEmpty()) {
            violations.add(new Violation("macroActivities", "At least one macroactivity is required before publication"));
        }

        for (int macroIndex = 0; macroIndex < version.macroActivities().size(); macroIndex++) {
            MacroActivity macro = version.macroActivities().get(macroIndex);
            String macroPath = "macroActivities[" + macroIndex + "]";

            if (macro.activities().isEmpty()) {
                violations.add(new Violation(
                        macroPath + ".activities",
                        "At least one activity is required before publication"
                ));
            }

            for (int activityIndex = 0; activityIndex < macro.activities().size(); activityIndex++) {
                Activity activity = macro.activities().get(activityIndex);
                String activityPath = macroPath + ".activities[" + activityIndex + "]";
                if (activity.name() == null || activity.name().isBlank()) {
                    violations.add(new Violation(activityPath + ".name", "Activity name is required"));
                }
                if (activity.instructions() == null || activity.instructions().isBlank()) {
                    violations.add(new Violation(activityPath + ".instructions", "Activity instructions are required"));
                }
            }

            List<EvidenceExpectation> expectations = allExpectations(macro);
            long ticketInputs = expectations.stream()
                    .filter(e -> e.location() == EvidenceLocation.INPUT)
                    .filter(e -> isTicket(e, registry))
                    .count();
            if (ticketInputs > 1) {
                violations.add(new Violation(
                        macroPath + ".inputs",
                        "A macroactivity may contain at most one TICKET input"
                ));
            }

            List<com.temaconsulting.agente_iso.process.domain.Cardinality> ticketCardinalities = expectations.stream()
                    .filter(e -> isTicket(e, registry))
                    .map(EvidenceExpectation::cardinality)
                    .distinct()
                    .toList();
            if (ticketCardinalities.size() > 1) {
                violations.add(new Violation(
                        macroPath,
                        "All TICKET expectations in a macroactivity must use the same cardinality"
                ));
            }

            for (EvidenceExpectation expectation : expectations) {
                String expectationPath = expectationPath(macro, expectation, macroPath);
                RegistryDefinition definition = registry.get(expectation.registryDefinitionId());
                if (definition == null) {
                    violations.add(new Violation(
                            expectationPath + ".registryDefinitionId",
                            "Registry definition does not exist: " + expectation.registryDefinitionId()
                    ));
                    continue;
                }
                if (expectation.applicability() == null) {
                    violations.add(new Violation(expectationPath + ".applicability", "Applicability is required"));
                }
                if (expectation.cardinality() == null) {
                    violations.add(new Violation(expectationPath + ".cardinality", "Cardinality is required"));
                }
                if (definition.requiresNomenclature()
                        && (definition.officialNomenclature() == null || definition.officialNomenclature().isBlank())) {
                    violations.add(new Violation(
                            expectationPath + ".registryDefinitionId",
                            "DOCUMENT/FOLDER registry definition requires official nomenclature"
                    ));
                }
            }
        }

        return List.copyOf(violations);
    }

    private static boolean isTicket(
            EvidenceExpectation expectation,
            Map<UUID, RegistryDefinition> registry
    ) {
        RegistryDefinition definition = registry.get(expectation.registryDefinitionId());
        return definition != null && definition.evidenceType() == EvidenceType.TICKET;
    }

    private static List<EvidenceExpectation> allExpectations(MacroActivity macro) {
        return Stream.concat(
                macro.inputs().stream(),
                Stream.concat(
                        macro.outputs().stream(),
                        macro.activities().stream().flatMap(a -> a.records().stream())
                )
        ).toList();
    }

    private static String expectationPath(
            MacroActivity macro,
            EvidenceExpectation expectation,
            String macroPath
    ) {
        if (expectation.location() == EvidenceLocation.INPUT) {
            return macroPath + ".inputs[" + macro.inputs().indexOf(expectation) + "]";
        }
        if (expectation.location() == EvidenceLocation.OUTPUT) {
            return macroPath + ".outputs[" + macro.outputs().indexOf(expectation) + "]";
        }
        for (int i = 0; i < macro.activities().size(); i++) {
            Activity activity = macro.activities().get(i);
            int recordIndex = activity.records().indexOf(expectation);
            if (recordIndex >= 0) {
                return macroPath + ".activities[" + i + "].records[" + recordIndex + "]";
            }
        }
        return macroPath + ".evidence";
    }
}
