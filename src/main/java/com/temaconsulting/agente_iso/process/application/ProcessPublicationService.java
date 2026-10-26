package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.application.port.ProcessVersionRepository;
import com.temaconsulting.agente_iso.process.application.error.StaleProcessVersionException;
import com.temaconsulting.agente_iso.process.application.model.Violation;

import com.temaconsulting.agente_iso.catalog.application.port.RegistryDefinitionRepository;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.process.domain.error.DraftNotEditableException;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.process.domain.error.RevisionMismatchException;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.model.FieldProblem;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.temaconsulting.agente_iso.process.application.ProcessService.conflict;
import static com.temaconsulting.agente_iso.process.application.ProcessService.notFound;
import static com.temaconsulting.agente_iso.process.application.ProcessService.staleRevision;

@Service
public class ProcessPublicationService {
    private static final Logger log = LoggerFactory.getLogger(ProcessPublicationService.class);

    private final ProcessVersionRepository versions;
    private final RegistryDefinitionRepository registry;
    private final PublicationValidator validator;
    private final Clock clock;

    public ProcessPublicationService(
            ProcessVersionRepository versions,
            RegistryDefinitionRepository registry,
            PublicationValidator validator,
            Clock clock
    ) {
        this.versions = versions;
        this.registry = registry;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public ProcessVersion publish(UUID processId, UUID versionId, int revision) {
        ProcessVersion version = versions.findWithTree(versionId)
                .orElseThrow(() -> notFound("Process version not found: " + versionId));

        if (!version.processId().equals(processId)) {
            throw notFound("Process version not found: " + versionId);
        }

        try {
            version.ensureEditableAt(revision);
        } catch (RevisionMismatchException ex) {
            throw staleRevision(ex.getMessage());
        } catch (DraftNotEditableException ex) {
            throw conflict(ex.getMessage());
        }

        List<RegistryDefinition> definitions = referencedRegistryIds(version).stream()
                .map(registry::findById)
                .flatMap(java.util.Optional::stream)
                .toList();

        List<Violation> violations = validator.validate(version, definitions);
        if (!violations.isEmpty()) {
            throw new ApiException(
                    ProblemCode.VALIDATION_ERROR,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "The draft cannot be published",
                    violations.stream()
                            .map(v -> new FieldProblem(v.field(), v.message()))
                            .toList()
            );
        }

        Instant publishedAt = clock.instant();
        version.publish(publishedAt);

        try {
            ProcessVersion published = versions.publish(versionId, revision, publishedAt);
            log.info("process_version_published processId={} versionId={} revision={}",
                    processId, versionId, revision);
            return published;
        } catch (StaleProcessVersionException ex) {
            ProcessVersion latest = versions.findWithTree(versionId).orElse(null);
            if (latest != null && !latest.isEditable()) {
                throw conflict("Process version is already published");
            }
            throw staleRevision(ex.getMessage());
        }
    }

    private static Set<UUID> referencedRegistryIds(ProcessVersion version) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (MacroActivity macro : version.macroActivities()) {
            Stream.concat(
                    macro.inputs().stream(),
                    Stream.concat(
                            macro.outputs().stream(),
                            macro.activities().stream().flatMap(a -> a.records().stream())
                    )
            ).map(EvidenceExpectation::registryDefinitionId).forEach(ids::add);
        }
        return ids;
    }
}
