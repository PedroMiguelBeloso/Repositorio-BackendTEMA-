package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.service.domain.ServiceType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record VerificationContext(
        ServiceContext service,
        ProcessContext process,
        VerificationScope scope,
        UUID macroActivityId,
        List<MacroActivityContext> macroActivities,
        List<ExpectationContext> expectations,
        List<TicketSelectionGroup> ticketSelectionGroups
) {
    public VerificationContext {
        Objects.requireNonNull(service);
        Objects.requireNonNull(process);
        Objects.requireNonNull(scope);
        macroActivities = List.copyOf(macroActivities);
        expectations = List.copyOf(expectations);
        ticketSelectionGroups = List.copyOf(ticketSelectionGroups);
        if (scope == VerificationScope.PROCESS && macroActivityId != null) {
            throw new IllegalArgumentException("PROCESS context cannot select a macroactivity");
        }
        if (scope == VerificationScope.MACRO_ACTIVITY && macroActivityId == null) {
            throw new IllegalArgumentException("MACRO_ACTIVITY context requires a macroactivity");
        }
    }

    public record ServiceContext(
            UUID id,
            ServiceType type,
            int revision,
            Source driveFolder,
            Source redmineProject
    ) {
        public ServiceContext {
            Objects.requireNonNull(id);
            Objects.requireNonNull(type);
            Objects.requireNonNull(driveFolder);
            Objects.requireNonNull(redmineProject);
        }
    }

    public record Source(SourceStatus status, String id, String name) {
        public Source {
            Objects.requireNonNull(status);
            if (status == SourceStatus.MISSING && (id != null || name != null)) {
                throw new IllegalArgumentException("Missing sources cannot have an ID or name");
            }
            if (status == SourceStatus.CONFIGURED && (id == null || name == null)) {
                throw new IllegalArgumentException("Configured sources require an ID and name");
            }
        }

        public static Source configured(String id, String name) {
            return new Source(SourceStatus.CONFIGURED, id, name);
        }

        public static Source missing() {
            return new Source(SourceStatus.MISSING, null, null);
        }
    }

    public enum SourceStatus {
        CONFIGURED,
        MISSING
    }

    public record ProcessContext(
            UUID versionId,
            UUID processId,
            ProcessCode code,
            String name,
            String officialVersion,
            ProcessStatus status,
            boolean m2Enabled
    ) {
        public ProcessContext {
            Objects.requireNonNull(versionId);
            Objects.requireNonNull(processId);
            Objects.requireNonNull(code);
            Objects.requireNonNull(name);
            Objects.requireNonNull(officialVersion);
            Objects.requireNonNull(status);
        }
    }

    public record MacroActivityContext(
            UUID id,
            String name,
            int order,
            List<ActivityContext> activities
    ) {
        public MacroActivityContext {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            activities = List.copyOf(activities);
        }
    }

    public record ActivityContext(UUID id, String name, int order) {
        public ActivityContext {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
        }
    }

    public record ExpectationContext(
            UUID expectationId,
            int order,
            UUID macroActivityId,
            UUID activityId,
            EvidenceLocation location,
            String displayName,
            ApplicabilityMode applicability,
            Cardinality cardinality,
            RegistryContext registry,
            String ticketSelectionGroupId
    ) {
        public ExpectationContext {
            Objects.requireNonNull(expectationId);
            Objects.requireNonNull(macroActivityId);
            Objects.requireNonNull(location);
            Objects.requireNonNull(displayName);
            Objects.requireNonNull(applicability);
            Objects.requireNonNull(cardinality);
            Objects.requireNonNull(registry);
        }
    }

    public record RegistryContext(
            UUID id,
            String key,
            String canonicalName,
            EvidenceType evidenceType,
            String officialNomenclature
    ) {
        public RegistryContext {
            Objects.requireNonNull(id);
            Objects.requireNonNull(key);
            Objects.requireNonNull(canonicalName);
            Objects.requireNonNull(evidenceType);
        }
    }
}
