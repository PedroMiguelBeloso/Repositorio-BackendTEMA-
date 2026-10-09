package com.temaconsulting.agente_iso.integration.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.temaconsulting.agente_iso.integration.application.IntegrationProbe;
import com.temaconsulting.agente_iso.integration.application.RedmineProjectCatalog;
import com.temaconsulting.agente_iso.integration.application.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.application.SourceProbeException;
import com.temaconsulting.agente_iso.integration.domain.ExternalProject;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.SourceHealth;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.service.application.ServiceSourceResolver;
import com.temaconsulting.agente_iso.service.application.ServiceTicketSearch;
import com.temaconsulting.agente_iso.service.application.TicketSummary;
import com.temaconsulting.agente_iso.service.domain.ExternalSource;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.FieldProblem;
import com.temaconsulting.agente_iso.shared.application.Page;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class RedmineAdapter implements IntegrationProbe, RedmineProjectCatalog, ServiceSourceResolver,
        ServiceTicketSearch, RedmineTicketReader {
    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public RedmineAdapter(
            RestClient.Builder builder,
            @Value("${tema.redmine.enabled:false}") boolean enabled,
            @Value("${tema.redmine.api-base-url:http://localhost:8080/redmine}") String baseUrl,
            @Value("${tema.redmine.api-key:}") String apiKey,
            @Value("${tema.redmine.timeout:PT5S}") String timeoutText
    ) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        Duration timeout = Duration.parse(timeoutText);
        if (timeout.toMillis() < 1 || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Redmine timeout must be between 1 ms and 30 s");
        }
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(timeout);
        requests.setReadTimeout(timeout);
        this.client = builder.baseUrl(this.baseUrl).requestFactory(requests).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public IntegrationSource source() {
        return IntegrationSource.REDMINE;
    }

    @Override
    public boolean isConfigured() {
        return enabled && !baseUrl.isBlank() && !apiKey.isBlank();
    }

    @Override
    public void checkReadable() {
        if (!enabled || baseUrl.isBlank() || apiKey.isBlank()) {
            throw new SourceProbeException(SourceHealth.UNAVAILABLE,
                    new IllegalStateException("Redmine is disabled or not configured"));
        }
        try {
            listProjects(0, 1);
        } catch (RedmineReadException ex) {
            throw new SourceProbeException(ex.health(), ex);
        }
    }

    @Override
    public Page<ExternalProject> listProjects(int page, int size) {
        return searchProjects("", page, size);
    }

    @Override
    public Page<ExternalProject> searchProjects(String query, int page, int size) {
        validatePage(page, size);
        requireConfigured();
        String needle = query == null ? "" : query.trim();
        List<ExternalProject> projects = new ArrayList<>();
        int offset = 0;
        while (true) {
            JsonNode payload = getJson("/projects.json", "offset", Integer.toString(offset), "limit", "100");
            JsonNode items = payload.get("projects");
            if (items == null || !items.isArray()) {
                throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                        "La respuesta de Redmine no incluye la lista de proyectos", List.of());
            }
            for (JsonNode item : items) {
                ExternalProject project = fromProject(item);
                if (needle.isEmpty() || project.name().toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))
                        || (project.identifier() != null && project.identifier().toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT)))) {
                    projects.add(project);
                }
            }
            int totalCount = payload.path("total_count").asInt(0);
            int limit = payload.path("limit").asInt(100);
            if (offset + items.size() >= totalCount || items.size() < limit) {
                break;
            }
            offset += limit;
            if (offset > 10000) {
                throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                        "Redmine devolvió demasiados proyectos para paginar de forma segura", List.of());
            }
        }
        List<ExternalProject> ordered = projects.stream()
                .sorted(Comparator.comparing(ExternalProject::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ExternalProject::id)).toList();
        long total = ordered.size();
        int from = Math.min(page * size, ordered.size());
        int to = Math.min(from + size, ordered.size());
        return new Page<>(ordered.subList(from, to), page, size, total);
    }

    @Override
    public ExternalProject findById(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        String normalized = projectId.trim();
        try {
            JsonNode payload = getJson("/projects/{id}.json", "id", normalized);
            JsonNode project = payload.get("project");
            if (project == null || project.isEmpty()) {
                return null;
            }
            return fromProject(project);
        } catch (RedmineReadException ex) {
            if (ex.health() == SourceHealth.PERMISSION_ERROR || ex.health() == SourceHealth.AUTH_ERROR) {
                throw sourceFailure(ex.health(), "La conexión de Redmine no tiene acceso a ese proyecto");
            }
            return null;
        } catch (ApiException ex) {
            if (ex.httpStatus().value() == 404) {
                return null;
            }
            throw ex;
        }
    }

    @Override
    public ExternalSource resolveDriveFolder(String folderId) {
        return null;
    }

    @Override
    public ExternalSource resolveRedmineProject(String projectId) {
        ExternalProject project = findById(projectId);
        if (project == null) {
            throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "El proyecto de Redmine seleccionado no es accesible", List.of());
        }
        return new ExternalSource(project.id(), project.name());
    }

    @Override
    public Page<TicketSummary> search(String projectId, String query, int page, int size) {
        validatePage(page, size);
        if (projectId == null || projectId.isBlank()) {
            throw invalid("projectId", "Project ID is required");
        }
        requireConfigured();
        findById(projectId);
        String needle = query == null ? "" : query.trim();
        List<RedmineIssue> issues = loadIssuesForProject(projectId);
        List<RedmineIssue> filtered = issues.stream()
                .filter(issue -> issue.matches(needle))
                .sorted(Comparator.comparing(RedmineIssue::updatedAt).reversed())
                .toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<TicketSummary> items = filtered.subList(from, to).stream()
                .map(issue -> new TicketSummary(issue.id(), issue.subject(), issue.status(), issue.assignedTo(), issue.updatedAt(), issue.projectId()))
                .toList();
        return new Page<>(items, page, size, filtered.size());
    }

    @Override
    public TicketSnapshot getTicket(String projectId, String ticketId) {
        if (projectId == null || projectId.isBlank()) {
            throw invalid("projectId", "Project ID is required");
        }
        if (ticketId == null || ticketId.isBlank()) {
            throw invalid("ticketId", "Ticket ID is required");
        }
        requireConfigured();
        JsonNode payload;
        try {
            payload = getJson("/issues/{id}.json", "id", ticketId.trim(), "include", "journals");
        } catch (RedmineReadException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RestClientResponseException responseException && responseException.getStatusCode().value() == 404) {
                throw new ApiException(ProblemCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "No se encontró el ticket solicitado", List.of());
            }
            throw sourceFailure(ex.health(), "No se pudo consultar el ticket en Redmine");
        }
        JsonNode issue = payload.get("issue");
        if (issue == null || issue.isEmpty()) {
            throw new ApiException(ProblemCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                    "No se encontró el ticket solicitado", List.of());
        }
        String actualProjectId = text(issue.path("project").path("id"));
        if (!projectId.equals(actualProjectId)) {
            throw new ApiException(ProblemCode.TICKET_OUTSIDE_PROJECT, HttpStatus.FORBIDDEN,
                    "El ticket no pertenece al proyecto configurado del servicio", List.of());
        }
        JsonNode journalsNode = issue.path("journals");
        if (journalsNode == null || journalsNode.isNull() || !journalsNode.isArray()) {
            throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "El ticket no incluye el historial requerido", List.of());
        }
        TicketSnapshot.CustomField[] customFields = parseCustomFields(issue.path("custom_fields"));
        TicketSnapshot.JournalEntry[] journals = parseJournals(journalsNode);
        return new TicketSnapshot(
                text(issue.path("id")),
                actualProjectId,
                text(issue.path("project").path("name")),
                text(issue.path("subject")),
                text(issue.path("status").path("name")),
                text(issue.path("status").path("id")),
                text(issue.path("status").path("name")),
                parseClosedStatus(issue.path("status")),
                text(issue.path("description")),
                text(issue.path("assigned_to").path("name")),
                parseInstant(issue.path("updated_on")),
                parseInstant(issue.path("created_on")),
                java.util.Arrays.asList(customFields),
                java.util.Arrays.asList(journals)
        );
    }

    private List<RedmineIssue> loadIssuesForProject(String projectId) {
        List<RedmineIssue> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            JsonNode payload = getJson("/issues.json", "project_id", projectId, "status_id", "*", "offset", Integer.toString(offset), "limit", "100");
            JsonNode items = payload.get("issues");
            if (items == null || !items.isArray()) {
                throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                        "La respuesta de Redmine no incluye la lista de tickets", List.of());
            }
            for (JsonNode item : items) {
                RedmineIssue issue = fromIssue(item);
                if (issue != null && projectId.equals(issue.projectId())) {
                    all.add(issue);
                }
            }
            int totalCount = payload.path("total_count").asInt(0);
            int limit = payload.path("limit").asInt(100);
            if (offset + items.size() >= totalCount || items.size() < limit) {
                break;
            }
            offset += limit;
        }
        return all;
    }

    private ExternalProject fromProject(JsonNode project) {
        String id = text(project.path("id"));
        String name = text(project.path("name"));
        String identifier = text(project.path("identifier"));
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "La respuesta de Redmine contiene un proyecto incompleto", List.of());
        }
        return new ExternalProject(id, name, identifier);
    }

    private RedmineIssue fromIssue(JsonNode issue) {
        String id = text(issue.path("id"));
        String projectId = text(issue.path("project").path("id"));
        String subject = text(issue.path("subject"));
        String status = text(issue.path("status").path("name"));
        String assignedTo = text(issue.path("assigned_to").path("name"));
        Instant updatedAt = parseInstant(issue.path("updated_on"));
        if (id == null || projectId == null || subject == null) {
            return null;
        }
        return new RedmineIssue(id, projectId, subject, status, assignedTo, updatedAt);
    }

    private JsonNode getJson(String path, String... params) {
        try {
            String requestPath = path;
            for (int index = 0; index < params.length; index += 2) {
                String key = params[index];
                String value = params[index + 1];
                if (key == null || value == null) {
                    continue;
                }
                if (path.contains("{id}") && "id".equals(key)) {
                    requestPath = requestPath.replace("{id}", value);
                }
            }
            final String resolvedPath = requestPath;
            String raw = client.get().uri(uriBuilder -> {
                        var builder = uriBuilder.path(resolvedPath);
                        for (int index = 0; index < params.length; index += 2) {
                            String key = params[index];
                            String value = params[index + 1];
                            if (key == null || value == null || (path.contains("{id}") && "id".equals(key))) {
                                continue;
                            }
                            builder = builder.queryParam(key, value);
                        }
                        return builder.build();
                    })
                    .headers(headers -> headers.set("X-Redmine-API-Key", apiKey))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw);
        } catch (IOException ex) {
            throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "La respuesta de Redmine no pudo leerse", List.of());
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new RedmineReadException(SourceHealth.UNAVAILABLE, ex);
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return String.valueOf(node.asLong());
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant parseInstant(JsonNode node) {
        if (node == null || node.isNull() || node.asText(null) == null) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private static TicketSnapshot.CustomField[] parseCustomFields(JsonNode customFields) {
        if (customFields == null || !customFields.isArray()) {
            return new TicketSnapshot.CustomField[0];
        }
        List<TicketSnapshot.CustomField> fields = new ArrayList<>();
        for (JsonNode field : customFields) {
            String id = text(field.path("id"));
            String name = text(field.path("name"));
            Object value = field.path("value").isContainerNode() ? field.path("value").toString() : text(field.path("value"));
            fields.add(new TicketSnapshot.CustomField(id, name, value));
        }
        return fields.toArray(TicketSnapshot.CustomField[]::new);
    }

    private static TicketSnapshot.JournalEntry[] parseJournals(JsonNode journals) {
        if (journals == null || !journals.isArray()) {
            return new TicketSnapshot.JournalEntry[0];
        }
        List<TicketSnapshot.JournalEntry> entries = new ArrayList<>();
        for (JsonNode journal : journals) {
            String id = text(journal.path("id"));
            String user = text(journal.path("user").path("name"));
            String notes = text(journal.path("notes"));
            Instant createdAt = parseInstant(journal.path("created_on"));
            List<TicketSnapshot.JournalChange> changes = parseJournalChanges(journal.path("details"));
            entries.add(new TicketSnapshot.JournalEntry(id, user, notes, createdAt, changes));
        }
        return entries.toArray(TicketSnapshot.JournalEntry[]::new);
    }

    private static List<TicketSnapshot.JournalChange> parseJournalChanges(JsonNode details) {
        if (details == null || !details.isArray()) {
            return List.of();
        }
        List<TicketSnapshot.JournalChange> changes = new ArrayList<>();
        for (JsonNode detail : details) {
            String field = text(detail.path("property"));
            Object oldValue = parseJsonValue(detail.path("old_value"));
            Object newValue = parseJsonValue(detail.path("new_value"));
            changes.add(new TicketSnapshot.JournalChange(field, oldValue, newValue));
        }
        return changes;
    }

    private static Object parseJsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray() || node.isObject()) {
            return node.toString();
        }
        return node.asText();
    }

    private static Boolean parseClosedStatus(JsonNode status) {
        if (status == null || status.isNull()) {
            return null;
        }
        if (status.hasNonNull("is_closed") && status.get("is_closed").isBoolean()) {
            return status.get("is_closed").asBoolean();
        }
        String normalized = text(status.path("name"));
        if (normalized == null) {
            return null;
        }
        String name = normalized.trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "cerrado", "closed", "resolved", "done", "terminado" -> true;
            default -> null;
        };
    }

    private RuntimeException translate(RestClientResponseException ex) {
        SourceHealth health = switch (ex.getStatusCode().value()) {
            case 401 -> SourceHealth.AUTH_ERROR;
            case 403 -> SourceHealth.PERMISSION_ERROR;
            default -> SourceHealth.UNAVAILABLE;
        };
        return new RedmineReadException(health, ex);
    }

    private ApiException sourceFailure(SourceHealth health, String message) {
        return switch (health) {
            case AUTH_ERROR -> new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "La conexión de Redmine no pudo autenticarse", List.of());
            case PERMISSION_ERROR -> new ApiException(ProblemCode.SOURCE_PERMISSION_DENIED, HttpStatus.FORBIDDEN,
                    message, List.of());
            default -> new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "La conexión de Redmine no está disponible", List.of());
        };
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "La conexión de Redmine no está configurada", List.of());
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new ApiException(ProblemCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "La página debe ser mayor o igual a cero", List.of(new FieldProblem("page", "La página debe ser mayor o igual a cero")));
        }
        if (size < 1 || size > 100) {
            throw new ApiException(ProblemCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "El tamaño debe estar entre 1 y 100", List.of(new FieldProblem("size", "El tamaño debe estar entre 1 y 100")));
        }
    }

    private ApiException invalid(String field, String message) {
        return new ApiException(ProblemCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                message, List.of(new FieldProblem(field, message)));
    }

    private record RedmineIssue(String id, String projectId, String subject, String status,
                               String assignedTo, Instant updatedAt) {
        private boolean matches(String needle) {
            if (needle == null || needle.isBlank()) {
                return true;
            }
            String normalized = needle.trim();
            if (normalized.matches("\\d+")) {
                return id.equals(normalized);
            }
            return subject.toLowerCase(Locale.ROOT).contains(normalized.toLowerCase(Locale.ROOT));
        }
    }
}
