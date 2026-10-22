package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.application.error.DuplicateOfficialVersionException;
import com.temaconsulting.agente_iso.process.application.port.ProcessRepository;
import com.temaconsulting.agente_iso.process.application.port.ProcessVersionRepository;
import com.temaconsulting.agente_iso.process.application.error.StaleProcessVersionException;

import com.temaconsulting.agente_iso.process.api.dto.ActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.CreateVersionRequest;
import com.temaconsulting.agente_iso.process.api.dto.EvidenceExpectationInput;
import com.temaconsulting.agente_iso.process.api.dto.MacroActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.ProcessPolicyInput;
import com.temaconsulting.agente_iso.process.api.dto.SaveDraftRequest;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.error.DomainValidationException;
import com.temaconsulting.agente_iso.process.domain.error.DraftNotEditableException;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessPolicy;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.process.domain.error.RevisionMismatchException;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.temaconsulting.agente_iso.process.application.ProcessService.conflict;
import static com.temaconsulting.agente_iso.process.application.ProcessService.notFound;
import static com.temaconsulting.agente_iso.process.application.ProcessService.staleRevision;
import static com.temaconsulting.agente_iso.process.application.ProcessService.validationError;

@Service
public class ProcessDraftService {

    private final ProcessRepository processes;
    private final ProcessVersionRepository versions;
    private final Clock clock;

    public ProcessDraftService(
            ProcessRepository processes,
            ProcessVersionRepository versions,
            Clock clock) {
        this.processes = processes;
        this.versions = versions;
        this.clock = clock;
    }

    public Page<ProcessVersion> listVersions(UUID processId, int page, int size) {
        requireExistingProcess(processId);
        validatePagination(page, size);
        return versions.listByProcess(processId, page, size);
    }

    public ProcessVersion getVersion(UUID processId, UUID versionId) {
        requireExistingProcess(processId);

        ProcessVersion version = versions.findWithTree(versionId)
                .orElseThrow(() -> notFound("Process version not found: " + versionId));

        if (!version.processId().equals(processId)) {
            throw notFound("Process version not found: " + versionId);
        }

        return version;
    }

    @Transactional
    public ProcessVersion createDraft(UUID processId, CreateVersionRequest request) {
        requireExistingProcess(processId);

        if (request == null) {
            throw validationError("request", "Request body is required");
        }
        if (request.officialVersion() == null || request.officialVersion().isBlank()) {
            throw validationError("officialVersion", "Official version is required");
        }
        if (request.versionDate() == null) {
            throw validationError("versionDate", "Version date is required");
        }

        String officialVersion = request.officialVersion().trim();
        if (versions.findByOfficialVersion(processId, officialVersion).isPresent()) {
            throw conflict("Official version already exists for this process: " + officialVersion);
        }

        final ProcessVersion draft;
        try {
            if (request.copyFromVersionId() == null) {
                draft = ProcessVersion.openDraft(
                        UUID.randomUUID(),
                        processId,
                        officialVersion,
                        request.versionDate(),
                        clock.instant());
            } else {
                ProcessVersion source = versions.findWithTree(request.copyFromVersionId())
                        .orElseThrow(() -> notFound("Source process version not found: " + request.copyFromVersionId()));

                if (!source.processId().equals(processId)) {
                    throw notFound("Source process version not found: " + request.copyFromVersionId());
                }

                draft = copyAsDraft(
                        source,
                        processId,
                        officialVersion,
                        request.versionDate(),
                        clock.instant()
                );
            }
        } catch (DomainValidationException ex) {
            throw validationError(ex.field(), ex.getMessage());
        }

        try {
            return versions.insertDraft(draft);
        } catch (DuplicateOfficialVersionException ex) {
            throw conflict("Official version already exists for this process: " + officialVersion);
        }
    }

    @Transactional
    public ProcessVersion saveDraft(UUID processId, UUID versionId, SaveDraftRequest request) {
        requireExistingProcess(processId);

        if (request == null) {
            throw validationError("request", "Request body is required");
        }

        ProcessVersion current = versions.findWithTree(versionId)
                .orElseThrow(() -> notFound("Process version not found: " + versionId));

        if (!current.processId().equals(processId)) {
            throw notFound("Process version not found: " + versionId);
        }

        try {
            current.ensureEditableAt(request.revision());
        } catch (RevisionMismatchException ex) {
            throw staleRevision(ex.getMessage());
        } catch (DraftNotEditableException ex) {
            throw conflict(ex.getMessage());
        }

        validateRequestCollections(request);
        validateReferencedIdsBelongToDraft(current, request);

        String requestedOfficialVersion = request.officialVersion() == null
                ? null
                : request.officialVersion().trim();
        if (requestedOfficialVersion != null && !requestedOfficialVersion.isBlank()) {
            versions.findByOfficialVersion(processId, requestedOfficialVersion)
                    .filter(existing -> !existing.id().equals(versionId))
                    .ifPresent(existing -> {
                        throw conflict(
                                "Official version already exists for this process: " + requestedOfficialVersion);
                    });
        }

        try {
            List<ProcessPolicy> policies = mapPolicies(request.policies());
            List<MacroActivity> macroActivities = mapMacroActivities(versionId, request.macroActivities());

            ProcessVersion updated = current.replaceStructure(
                    request.revision(),
                    request.officialVersion(),
                    request.versionDate(),
                    request.objective(),
                    request.owner(),
                    policies,
                    macroActivities);

            return versions.replaceDraft(updated, request.revision());
        } catch (DomainValidationException ex) {
            throw validationError(ex.field(), ex.getMessage());
        } catch (RevisionMismatchException ex) {
            throw staleRevision(ex.getMessage());
        } catch (DraftNotEditableException ex) {
            throw conflict(ex.getMessage());
        } catch (StaleProcessVersionException ex) {
            throw staleRevision(ex.getMessage());
        } catch (DuplicateOfficialVersionException ex) {
            throw conflict("Official version already exists for this process: " + request.officialVersion());
        }
    }

    private static ProcessVersion copyAsDraft(
            ProcessVersion source,
            UUID processId,
            String officialVersion,
            java.time.LocalDate versionDate,
            java.time.Instant createdAt
    ) {
        UUID newVersionId = UUID.randomUUID();

        List<ProcessPolicy> policies = source.policies().stream()
                .map(policy -> new ProcessPolicy(
                        UUID.randomUUID(),
                        policy.order(),
                        policy.text()
                ))
                .toList();

        List<MacroActivity> macros = source.macroActivities().stream()
                .map(sourceMacro -> {
                    UUID newMacroId = UUID.randomUUID();

                    List<EvidenceExpectation> inputs = sourceMacro.inputs().stream()
                            .map(e -> copyExpectation(e, newMacroId, null, EvidenceLocation.INPUT))
                            .toList();
                    List<EvidenceExpectation> outputs = sourceMacro.outputs().stream()
                            .map(e -> copyExpectation(e, newMacroId, null, EvidenceLocation.OUTPUT))
                            .toList();

                    List<Activity> activities = sourceMacro.activities().stream()
                            .map(sourceActivity -> {
                                UUID newActivityId = UUID.randomUUID();
                                List<EvidenceExpectation> records = sourceActivity.records().stream()
                                        .map(e -> copyExpectation(
                                                e,
                                                newMacroId,
                                                newActivityId,
                                                EvidenceLocation.ACTIVITY_RECORD
                                        ))
                                        .toList();
                                return new Activity(
                                        newActivityId,
                                        newMacroId,
                                        sourceActivity.name(),
                                        sourceActivity.responsible(),
                                        sourceActivity.instructions(),
                                        sourceActivity.etaText(),
                                        sourceActivity.order(),
                                        records
                                );
                            })
                            .toList();

                    return new MacroActivity(
                            newMacroId,
                            newVersionId,
                            sourceMacro.name(),
                            sourceMacro.description(),
                            sourceMacro.order(),
                            activities,
                            inputs,
                            outputs
                    );
                })
                .toList();

        return new ProcessVersion(
                newVersionId,
                processId,
                officialVersion,
                versionDate,
                com.temaconsulting.agente_iso.process.domain.ProcessStatus.DRAFT,
                0,
                source.objective(),
                source.owner(),
                policies,
                macros,
                null,
                createdAt
        );
    }

    private static EvidenceExpectation copyExpectation(
            EvidenceExpectation source,
            UUID macroActivityId,
            UUID activityId,
            EvidenceLocation location
    ) {
        return new EvidenceExpectation(
                UUID.randomUUID(),
                macroActivityId,
                activityId,
                source.registryDefinitionId(),
                source.displayName(),
                source.applicability(),
                source.cardinality(),
                location,
                source.order()
        );
    }

    private List<ProcessPolicy> mapPolicies(List<ProcessPolicyInput> inputs) {
        return inputs.stream()
                .map(input -> {
                    if (input == null) {
                        throw validationError("policies", "Policies cannot contain null elements");
                    }
                    return new ProcessPolicy(
                            input.id() == null ? UUID.randomUUID() : input.id(),
                            input.order(),
                            input.text());
                })
                .toList();
    }

    private List<MacroActivity> mapMacroActivities(UUID versionId, List<MacroActivityInput> inputs) {
        return inputs.stream()
                .map(input -> {
                    if (input == null) {
                        throw validationError("macroActivities", "Macroactivities cannot contain null elements");
                    }

                    UUID macroActivityId = input.id() == null ? UUID.randomUUID() : input.id();

                    List<EvidenceExpectation> macroInputs = mapExpectations(input.inputs(), macroActivityId, null,
                            EvidenceLocation.INPUT);
                    List<EvidenceExpectation> macroOutputs = mapExpectations(input.outputs(), macroActivityId, null,
                            EvidenceLocation.OUTPUT);

                    List<Activity> activities = input.activities().stream()
                            .map(activity -> mapActivity(macroActivityId, activity))
                            .toList();

                    // Orden exacto del record MacroActivity de tu equipo:
                    // id, processVersionId, name, description, order, activities, inputs, outputs
                    return new MacroActivity(
                            macroActivityId,
                            versionId,
                            input.name(),
                            input.description(),
                            input.order(),
                            activities,
                            macroInputs,
                            macroOutputs);
                })
                .toList();
    }

    private Activity mapActivity(UUID macroActivityId, ActivityInput input) {
        if (input == null) {
            throw validationError(
                    "macroActivities.activities",
                    "Activities cannot contain null elements");
        }
        UUID activityId = input.id() == null ? UUID.randomUUID() : input.id();
        List<EvidenceExpectation> records = mapExpectations(input.records(), macroActivityId, activityId,
                EvidenceLocation.ACTIVITY_RECORD);

        // Orden exacto del record Activity de tu equipo:
        // id, macroActivityId, name, responsible, instructions, etaText, order, records
        return new Activity(
                activityId,
                macroActivityId,
                input.name(),
                input.responsible(),
                input.instructions(),
                input.etaText(),
                input.order(),
                records);
    }

    private List<EvidenceExpectation> mapExpectations(List<EvidenceExpectationInput> list, UUID macroId,
            UUID activityId, EvidenceLocation loc) {
        if (list == null)
            return List.of();
        return list.stream().map(e -> new EvidenceExpectation(
                e.id() == null ? UUID.randomUUID() : e.id(),
                macroId,
                activityId,
                e.registryDefinitionId(),
                e.displayName(),
                e.applicability(),
                e.cardinality(),
                loc,
                e.order())).toList();
    }

    private void validateReferencedIdsBelongToDraft(ProcessVersion current, SaveDraftRequest request) {
        Set<UUID> currentPolicyIds = current.policies().stream()
                .map(ProcessPolicy::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> currentMacroActivityIds = current.macroActivities().stream()
                .map(MacroActivity::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> currentActivityIds = current.macroActivities().stream()
                .flatMap(macro -> macro.activities().stream())
                .map(Activity::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> currentExpectationIds = current.macroActivities().stream()
                .flatMap(macro -> java.util.stream.Stream.concat(
                        macro.inputs().stream(),
                        java.util.stream.Stream.concat(
                                macro.outputs().stream(),
                                macro.activities().stream().flatMap(activity -> activity.records().stream())
                        )
                ))
                .map(EvidenceExpectation::id)
                .collect(java.util.stream.Collectors.toSet());

        Set<UUID> seenPolicyIds = new HashSet<>();
        for (ProcessPolicyInput policy : request.policies()) {
            if (policy == null || policy.id() == null) {
                continue;
            }
            requireOwnedId(policy.id(), currentPolicyIds, "policies.id");
            requireUniqueRequestId(policy.id(), seenPolicyIds, "policies.id");
        }

        Set<UUID> seenMacroIds = new HashSet<>();
        Set<UUID> seenActivityIds = new HashSet<>();
        Set<UUID> seenExpectationIds = new HashSet<>();
        for (MacroActivityInput macro : request.macroActivities()) {
            if (macro == null) {
                continue;
            }
            if (macro.id() != null) {
                requireOwnedId(macro.id(), currentMacroActivityIds, "macroActivities.id");
                requireUniqueRequestId(macro.id(), seenMacroIds, "macroActivities.id");
            }

            validateExpectationIds(
                    macro.inputs(),
                    currentExpectationIds,
                    seenExpectationIds,
                    "macroActivities.inputs.id"
            );
            validateExpectationIds(
                    macro.outputs(),
                    currentExpectationIds,
                    seenExpectationIds,
                    "macroActivities.outputs.id"
            );

            if (macro.activities() == null) {
                continue;
            }
            for (ActivityInput activity : macro.activities()) {
                if (activity == null) {
                    continue;
                }
                if (activity.id() != null) {
                    requireOwnedId(activity.id(), currentActivityIds, "macroActivities.activities.id");
                    requireUniqueRequestId(
                            activity.id(),
                            seenActivityIds,
                            "macroActivities.activities.id");
                }
                validateExpectationIds(
                        activity.records(),
                        currentExpectationIds,
                        seenExpectationIds,
                        "macroActivities.activities.records.id"
                );
            }
        }
    }

    private static void validateExpectationIds(
            List<EvidenceExpectationInput> expectations,
            Set<UUID> currentIds,
            Set<UUID> seenIds,
            String field
    ) {
        if (expectations == null) {
            return;
        }
        for (EvidenceExpectationInput expectation : expectations) {
            if (expectation == null || expectation.id() == null) {
                continue;
            }
            requireOwnedId(expectation.id(), currentIds, field);
            requireUniqueRequestId(expectation.id(), seenIds, field);
        }
    }

    private static void requireOwnedId(UUID id, Set<UUID> currentIds, String field) {
        if (!currentIds.contains(id)) {
            throw validationError(field, "ID does not belong to this draft: " + id);
        }
    }

    private static void requireUniqueRequestId(UUID id, Set<UUID> seen, String field) {
        if (!seen.add(id)) {
            throw validationError(field, "Repeated ID in request: " + id);
        }
    }

    private static void validateRequestCollections(SaveDraftRequest request) {
        if (request.policies() == null) {
            throw validationError("policies", "Policies are required");
        }
        if (request.macroActivities() == null) {
            throw validationError("macroActivities", "Macroactivities are required");
        }
        for (MacroActivityInput macro : request.macroActivities()) {
            if (macro == null) {
                continue;
            }
            if (macro.inputs() == null) {
                throw validationError("macroActivities.inputs", "Inputs are required");
            }
            if (macro.activities() == null) {
                throw validationError("macroActivities.activities", "Activities are required");
            }
            if (macro.outputs() == null) {
                throw validationError("macroActivities.outputs", "Outputs are required");
            }
            for (ActivityInput activity : macro.activities()) {
                if (activity != null && activity.records() == null) {
                    throw validationError(
                            "macroActivities.activities.records",
                            "Activity records are required");
                }
            }
        }
    }

    private void requireExistingProcess(UUID processId) {
        if (processId == null || processes.findById(processId).isEmpty()) {
            throw notFound("Process not found: " + processId);
        }
    }

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw validationError("page", "Page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw validationError("size", "Size must be between 1 and 100");
        }
    }
}