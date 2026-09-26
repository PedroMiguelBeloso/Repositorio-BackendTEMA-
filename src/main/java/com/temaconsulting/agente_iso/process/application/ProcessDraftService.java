package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.api.dto.ActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.CreateVersionRequest;
import com.temaconsulting.agente_iso.process.api.dto.MacroActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.ProcessPolicyInput;
import com.temaconsulting.agente_iso.process.api.dto.SaveDraftRequest;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.DomainValidationException;
import com.temaconsulting.agente_iso.process.domain.DraftNotEditableException;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessPolicy;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.process.domain.RevisionMismatchException;
import com.temaconsulting.agente_iso.shared.application.Page;
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
            Clock clock
    ) {
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
        if (request.copyFromVersionId() != null) {
            throw conflict("copyFromVersionId is not supported before work package 5.1.4");
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
            draft = ProcessVersion.openDraft(
                    UUID.randomUUID(),
                    processId,
                    officialVersion,
                    request.versionDate(),
                    clock.instant()
            );
        } catch (DomainValidationException ex) {
            throw validationError(ex.field(), ex.getMessage());
        }

        try {
            return versions.insertDraft(draft);
        } catch (DuplicateOfficialVersionException ex) {
            throw conflict("Official version already exists for this process: " + officialVersion);
        }
    }

    /**
     * Completes the WBS 5.1.2 part of the draft tree. The HTTP PUT is intentionally
     * not exposed yet: evidence inputs/records/outputs are integrated in WBS 5.1.3.
     */
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
        rejectEvidenceUntilNextWorkPackage(request);
        validateReferencedIdsBelongToDraft(current, request);

        String requestedOfficialVersion = request.officialVersion() == null
                ? null
                : request.officialVersion().trim();
        if (requestedOfficialVersion != null && !requestedOfficialVersion.isBlank()) {
            versions.findByOfficialVersion(processId, requestedOfficialVersion)
                    .filter(existing -> !existing.id().equals(versionId))
                    .ifPresent(existing -> {
                        throw conflict(
                                "Official version already exists for this process: " + requestedOfficialVersion
                        );
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
                    macroActivities
            );

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

    private List<ProcessPolicy> mapPolicies(List<ProcessPolicyInput> inputs) {
        return inputs.stream()
                .map(input -> {
                    if (input == null) {
                        throw validationError("policies", "Policies cannot contain null elements");
                    }
                    return new ProcessPolicy(
                            input.id() == null ? UUID.randomUUID() : input.id(),
                            input.order(),
                            input.text()
                    );
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
                    List<Activity> activities = input.activities().stream()
                            .map(activity -> mapActivity(macroActivityId, activity))
                            .toList();

                    return new MacroActivity(
                            macroActivityId,
                            versionId,
                            input.name(),
                            input.description(),
                            input.order(),
                            activities
                    );
                })
                .toList();
    }

    private Activity mapActivity(UUID macroActivityId, ActivityInput input) {
        if (input == null) {
            throw validationError(
                    "macroActivities.activities",
                    "Activities cannot contain null elements"
            );
        }
        return new Activity(
                input.id() == null ? UUID.randomUUID() : input.id(),
                macroActivityId,
                input.name(),
                input.responsible(),
                input.instructions(),
                input.etaText(),
                input.order()
        );
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
        for (MacroActivityInput macro : request.macroActivities()) {
            if (macro == null) {
                continue;
            }
            if (macro.id() != null) {
                requireOwnedId(macro.id(), currentMacroActivityIds, "macroActivities.id");
                requireUniqueRequestId(macro.id(), seenMacroIds, "macroActivities.id");
            }
            if (macro.activities() == null) {
                continue;
            }
            for (ActivityInput activity : macro.activities()) {
                if (activity == null || activity.id() == null) {
                    continue;
                }
                requireOwnedId(activity.id(), currentActivityIds, "macroActivities.activities.id");
                requireUniqueRequestId(
                        activity.id(),
                        seenActivityIds,
                        "macroActivities.activities.id"
                );
            }
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
                            "Activity records are required"
                    );
                }
            }
        }
    }

    private static void rejectEvidenceUntilNextWorkPackage(SaveDraftRequest request) {
        for (MacroActivityInput macro : request.macroActivities()) {
            if (macro == null) {
                continue;
            }
            if (!macro.inputs().isEmpty() || !macro.outputs().isEmpty()) {
                throw validationError(
                        "macroActivities",
                        "Inputs and outputs are implemented in work package 5.1.3"
                );
            }
            for (ActivityInput activity : macro.activities()) {
                if (activity != null && !activity.records().isEmpty()) {
                    throw validationError(
                            "macroActivities.activities.records",
                            "Activity records are implemented in work package 5.1.3"
                    );
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
