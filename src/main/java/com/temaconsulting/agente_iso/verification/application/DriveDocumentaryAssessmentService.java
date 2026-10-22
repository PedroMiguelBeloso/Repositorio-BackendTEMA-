package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.integration.application.port.DriveEvidenceSearch;
import com.temaconsulting.agente_iso.integration.application.port.DriveEvidenceSearch.Cardinality;
import com.temaconsulting.agente_iso.integration.application.port.DriveEvidenceSearch.Kind;
import com.temaconsulting.agente_iso.integration.application.port.DriveEvidenceSearch.Match;
import com.temaconsulting.agente_iso.integration.application.port.DriveEvidenceSearch.Problem;
import com.temaconsulting.agente_iso.integration.application.port.DriveEvidenceSearch.SearchRequest;
import com.temaconsulting.agente_iso.integration.application.port.DriveEvidenceSearch.SearchResult;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Proposes a documentary result; the verification run and human decisions are handled by M2. */
@Service
public class DriveDocumentaryAssessmentService {
    private static final String SCOPE_LIMITATION =
            "Solo se comprobó la existencia y la nomenclatura; no se revisó el contenido, la completitud ni la aprobación.";

    private final DriveEvidenceSearch search;
    private final Clock clock;

    public DriveDocumentaryAssessmentService(DriveEvidenceSearch search, Clock clock) {
        this.search = Objects.requireNonNull(search);
        this.clock = Objects.requireNonNull(clock);
    }

    /** The caller supplies an applicability decision; this service never infers it from a file name. */
    public Assessment assess(SearchRequest request, ApplicabilityDecision applicability) {
        Objects.requireNonNull(applicability, "applicability");
        if (applicability == ApplicabilityDecision.UNDETERMINED) {
            return new Assessment(ResultStatus.REVIEW_REQUIRED,
                    "Todavía no se determinó si corresponde exigir este registro.",
                    List.of("No se consultó Google Drive hasta resolver la aplicabilidad."), List.of());
        }
        if (applicability == ApplicabilityDecision.NOT_APPLICABLE) {
            return new Assessment(ResultStatus.NOT_APPLICABLE,
                    "El registro fue declarado no aplicable por el flujo de aplicabilidad.",
                    List.of(), List.of());
        }

        Objects.requireNonNull(request, "request");
        SearchResult result = search.search(request);
        if (result == null || result.outcome() == null) {
            return new Assessment(ResultStatus.REVIEW_REQUIRED,
                    "Google Drive no devolvió un resultado de búsqueda confiable.",
                    List.of("La respuesta de búsqueda fue nula o inconsistente."), List.of());
        }
        Instant observedAt = clock.instant();
        List<EvidenceReference> references = result.matches().stream()
                .map(match -> reference(match, observedAt))
                .toList();
        if (result.outcome() != DriveEvidenceSearch.Outcome.NOT_CHECKABLE && result.problem() != null) {
            return inconsistent(references);
        }

        return switch (result.outcome()) {
            case FOUND -> found(request, references);
            case ABSENT -> references.isEmpty()
                    ? new Assessment(ResultStatus.DOES_NOT_COMPLY,
                            "La búsqueda completa no encontró la evidencia con la nomenclatura configurada.",
                            List.of(), references)
                    : inconsistent(references);
            case AMBIGUOUS -> new Assessment(ResultStatus.REVIEW_REQUIRED,
                    "Se esperaba una evidencia, pero se encontraron varias coincidencias.",
                    List.of("Debe revisarse cuál de las coincidencias corresponde al registro esperado."),
                    references);
            case NOT_CHECKABLE -> new Assessment(ResultStatus.REVIEW_REQUIRED,
                    "No se pudo completar una comprobación confiable en Google Drive.",
                    List.of(problemMessage(result.problem())), references);
        };
    }

    private static Assessment found(SearchRequest request, List<EvidenceReference> references) {
        if (references.isEmpty()
                || (request.cardinality() == Cardinality.ONE && references.size() != 1)
                || references.stream().anyMatch(reference -> reference.kind() != request.kind()
                        || reference.externalId() == null || reference.externalId().isBlank()
                        || reference.displayName() == null || reference.displayName().isBlank()
                        || !matchesNomenclature(request, reference))) {
            return new Assessment(ResultStatus.REVIEW_REQUIRED,
                    "La búsqueda devolvió coincidencias incompatibles con la expectativa configurada.",
                    List.of("Revisar la configuración y los resultados antes de emitir un dictamen."),
                    references);
        }
        return new Assessment(ResultStatus.COMPLIES,
                "Se encontró la cantidad requerida de evidencias con la nomenclatura configurada. "
                        + "Esto acredita solo su existencia y nomenclatura; no se revisó el contenido, "
                        + "la completitud ni la aprobación.",
                List.of(SCOPE_LIMITATION), references);
    }

    private static boolean matchesNomenclature(SearchRequest request, EvidenceReference reference) {
        if (request.officialNomenclature() == null || request.officialNomenclature().isBlank()) {
            return false;
        }
        String[] expectedPath = request.officialNomenclature().split("/", -1);
        String expectedName = expectedPath[expectedPath.length - 1];
        if (!expectedName.equals(reference.displayName())) {
            return false;
        }
        if (expectedPath.length > 1) {
            return request.officialNomenclature().equals(reference.relativePath());
        }
        return reference.relativePath() == null
                || reference.relativePath().equals(expectedName)
                || reference.relativePath().endsWith("/" + expectedName);
    }

    private static Assessment inconsistent(List<EvidenceReference> references) {
        return new Assessment(ResultStatus.REVIEW_REQUIRED,
                "La búsqueda devolvió datos inconsistentes con el estado informado.",
                List.of("Revisar la respuesta de Google Drive antes de emitir un dictamen."),
                references);
    }

    private static EvidenceReference reference(Match match, Instant observedAt) {
        return new EvidenceReference(match.kind(), IntegrationSource.DRIVE, match.externalId(),
                match.name(), match.webUrl(), match.relativePath(), observedAt);
    }

    private static String problemMessage(Problem problem) {
        if (problem == null) return "La fuente no indicó por qué no pudo completarse la búsqueda.";
        return switch (problem) {
            case NOT_CONFIGURED -> "La conexión de Google Drive no está configurada.";
            case MISSING_FOLDER -> "No hay una carpeta válida de Drive asociada al servicio.";
            case INVALID_EXPECTATION -> "La expectativa documental no tiene una configuración válida.";
            case AUTH_ERROR -> "Google Drive rechazó la autenticación.";
            case PERMISSION_ERROR -> "La cuenta conectada no tiene permiso para consultar la carpeta.";
            case UNAVAILABLE -> "Google Drive no respondió o no estuvo disponible.";
            case INCOMPLETE -> "La búsqueda terminó antes de revisar todo el ámbito configurado.";
        };
    }

    /** Internal reference. The eventual API mapper follows the OpenAPI EvidenceReference schema. */
    public record EvidenceReference(Kind kind, IntegrationSource source, String externalId,
                                    String displayName, String webUrl, String relativePath,
                                    Instant observedAt) { }

    public record Assessment(ResultStatus proposedStatus, String rationale,
                             List<String> limitations, List<EvidenceReference> evidence) {
        public Assessment {
            limitations = List.copyOf(limitations);
            evidence = List.copyOf(evidence);
        }
    }
}
