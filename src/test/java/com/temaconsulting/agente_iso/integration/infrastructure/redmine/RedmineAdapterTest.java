package com.temaconsulting.agente_iso.integration.infrastructure.redmine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.temaconsulting.agente_iso.integration.application.error.SourceProbeException;
import com.temaconsulting.agente_iso.service.application.model.TicketSummary;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedmineAdapterTest {
    private HttpServer server;
    private final List<String> requests = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redmine/projects", this::respondProject);
        server.createContext("/redmine/issues", this::respondIssue);
        server.createContext("/redmine/issues/", this::respondIssueDetail);
        server.createContext("/redmine/projects/12.json", this::respondProjectDetail);
        server.createContext("/redmine/projects/999.json", this::respondProjectDetail);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void disabledConnectorStartsWithoutCredentialsAndDoesNotRead() {
        RedmineAdapter redmine = adapter(false);
        assertThat(redmine.isConfigured()).isFalse();
        assertThatThrownBy(redmine::checkReadable)
                .isInstanceOf(SourceProbeException.class)
                .hasMessageContaining("disabled");
        assertThat(requests).isEmpty();
    }

    @Test
    void listsProjectsWithPaginationAndValidatesProjectSelection() {
        RedmineAdapter redmine = adapter(true);

        var first = redmine.listProjects(0, 1);
        var second = redmine.listProjects(1, 1);

        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1);
        assertThat(first.items().getFirst().id()).isNotEqualTo(second.items().getFirst().id());
        assertThat(redmine.resolveRedmineProject("12").name()).isEqualTo("Proyecto principal");
        assertThatThrownBy(() -> redmine.resolveRedmineProject("999"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no es accesible");
    }

    @Test
    void searchesOnlyTicketsInsideTheConfiguredProject() {
        RedmineAdapter redmine = adapter(true);

        var page = redmine.search("12", "", 0, 20);
        var byId = redmine.search("12", "204", 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).allMatch(ticket -> ticket.projectId().equals("12"));
        assertThat(byId.items()).extracting(TicketSummary::id).containsExactly("204");
        assertThat(requests).allMatch(request -> !request.contains("X-Redmine-API-Key"));
    }

    @Test
    void readsSelectedTicketWithHistoryAndRejectsOtherProject() {
        RedmineAdapter redmine = adapter(true);

        var ticket = redmine.getTicket("12", "204");

        assertThat(ticket.projectId()).isEqualTo("12");
        assertThat(ticket.subject()).isEqualTo("Fallo de integración");
        assertThat(ticket.statusId()).isEqualTo("5");
        assertThat(ticket.statusName()).isEqualTo("Cerrado");
        assertThat(ticket.status()).isEqualTo("Cerrado");
        assertThat(ticket.closed()).isTrue();
        assertThat(ticket.customFields()).extracting(field -> field.name()).contains("Prioridad");
        assertThat(ticket.journals()).hasSize(2);
        assertThat(ticket.journals().getFirst().notes()).isEqualTo("Se cerró");
        assertThat(ticket.journals().getFirst().changes()).extracting(change -> change.field())
                .contains("status_id");
        assertThat(ticket.journals().getFirst().changes()).extracting(change -> change.newValue())
                .contains("5");
        assertThatThrownBy(() -> redmine.getTicket("12", "999"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No se encontró");
        assertThatThrownBy(() -> redmine.getTicket("22", "204"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no pertenece");
        assertThat(requests).anyMatch(request -> request.contains("/redmine/issues/204.json?include=journals"));
    }

    @Test
    void missingJournalsAreRejectedAsUnreadableHistory() throws IOException {
        HttpServer issueOnlyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issueOnlyServer.createContext("/redmine/issues/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"));
            write(exchange, 200, """
                    {
                      "issue": {
                        "id": 204,
                        "project": {"id": 12, "name": "Proyecto principal"},
                        "subject": "Fallo de integración",
                        "status": {"id": 5, "name": "Cerrado"},
                        "assigned_to": {"name": "Ana"},
                        "updated_on": "2026-10-02T11:00:00Z",
                        "custom_fields": [{"id": 1, "name": "Prioridad", "value": "Alta"}]
                      }
                    }
                    """);
        });
        issueOnlyServer.start();
        try {
            RedmineAdapter redmine = new RedmineAdapter(RestClient.builder(), true,
                    "http://127.0.0.1:" + issueOnlyServer.getAddress().getPort() + "/redmine",
                    "test-redmine-key", "PT1S");

            assertThatThrownBy(() -> redmine.getTicket("12", "204"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("historial");
        } finally {
            issueOnlyServer.stop(0);
        }
    }

    @Test
    void ticketReadFailuresBecomeSafeProblemDetailsWithoutRemoteResponseContent() throws IOException {
        HttpServer unavailableServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        unavailableServer.createContext("/redmine/issues/", exchange ->
                write(exchange, 503, "{\"error\":\"secret-api-key must not leak\"}"));
        unavailableServer.start();
        try {
            RedmineAdapter redmine = new RedmineAdapter(RestClient.builder(), true,
                    "http://127.0.0.1:" + unavailableServer.getAddress().getPort() + "/redmine",
                    "test-redmine-key", "PT1S");

            assertThatThrownBy(() -> redmine.getTicket("12", "204"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> {
                        ApiException apiError = (ApiException) error;
                        assertThat(apiError.code()).isEqualTo(ProblemCode.SOURCE_UNAVAILABLE);
                        assertThat(apiError.httpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(apiError.safeMessage()).doesNotContain("secret-api-key");
                    });
        } finally {
            unavailableServer.stop(0);
        }
    }

    @Test
    void unknownStatusIsNotGuessedWhenRedmineDoesNotSignalClosed() throws IOException {
        HttpServer unknownStatusServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        unknownStatusServer.createContext("/redmine/issues/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"));
            write(exchange, 200, """
                    {
                      "issue": {
                        "id": 204,
                        "project": {"id": 12, "name": "Proyecto principal"},
                        "subject": "Fallo de integración",
                        "status": {"id": 7, "name": "En revisión"},
                        "assigned_to": {"name": "Ana"},
                        "updated_on": "2026-10-02T11:00:00Z",
                        "journals": []
                      }
                    }
                    """);
        });
        unknownStatusServer.start();
        try {
            RedmineAdapter redmine = new RedmineAdapter(RestClient.builder(), true,
                    "http://127.0.0.1:" + unknownStatusServer.getAddress().getPort() + "/redmine",
                    "test-redmine-key", "PT1S");

            var ticket = redmine.getTicket("12", "204");
            assertThat(ticket.statusId()).isEqualTo("7");
            assertThat(ticket.statusName()).isEqualTo("En revisión");
            assertThat(ticket.closed()).isNull();
            assertThat(ticket.journals()).isEmpty();
        } finally {
            unknownStatusServer.stop(0);
        }
    }

    private RedmineAdapter adapter(boolean enabled) {
        return new RedmineAdapter(RestClient.builder(), enabled,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/redmine",
                "test-redmine-key", "PT1S");
    }

    private void respondProject(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"));
        if (!"test-redmine-key".equals(exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"))) {
            write(exchange, 401, "{\"errors\":[\"unauthorized\"]}");
            return;
        }
        if (exchange.getRequestURI().getQuery() != null && exchange.getRequestURI().getQuery().contains("offset=1")) {
            write(exchange, 200, """
                    {
                      "projects": [{"id": 22, "name": "Proyecto secundario", "identifier": "proyecto-secundario"}],
                      "total_count": 2,
                      "offset": 1,
                      "limit": 1
                    }
                    """);
            return;
        }
        write(exchange, 200, """
                {
                  "projects": [{"id": 12, "name": "Proyecto principal", "identifier": "proyecto-principal"}],
                  "total_count": 2,
                  "offset": 0,
                  "limit": 1
                }
                """);
    }

    private void respondProjectDetail(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"));
        if (!"test-redmine-key".equals(exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"))) {
            write(exchange, 401, "{\"errors\":[\"unauthorized\"]}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/999.json")) {
            write(exchange, 404, "{\"error\":\"Not Found\"}");
            return;
        }
        write(exchange, 200, """
                {
                  "project": {"id": 12, "name": "Proyecto principal", "identifier": "proyecto-principal"}
                }
                """);
    }

    private void respondIssue(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"));
        if (!"test-redmine-key".equals(exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"))) {
            write(exchange, 401, "{\"errors\":[\"unauthorized\"]}");
            return;
        }
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null && query.contains("project_id=12") && query.contains("status_id=*")) {
            write(exchange, 200, """
                    {
                      "issues": [
                        {"id": 204, "project": {"id": 12, "name": "Proyecto principal"}, "subject": "Fallo de integración", "status": {"id": 5, "name": "Cerrado"}, "assigned_to": {"name": "Ana"}, "updated_on": "2026-10-02T11:00:00Z"},
                        {"id": 205, "project": {"id": 12, "name": "Proyecto principal"}, "subject": "Tarea abierta", "status": {"id": 1, "name": "Abierto"}, "assigned_to": {"name": "Luis"}, "updated_on": "2026-10-03T09:00:00Z"},
                        {"id": 999, "project": {"id": 35, "name": "Otro proyecto"}, "subject": "Ticket ajeno", "status": {"id": 1, "name": "Abierto"}, "assigned_to": {"name": "X"}, "updated_on": "2026-10-03T09:00:00Z"}
                      ],
                      "total_count": 3,
                      "offset": 0,
                      "limit": 3
                    }
                    """);
            return;
        }
        write(exchange, 400, "{\"errors\":[\"bad request\"]}");
    }

    private void respondIssueDetail(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"));
        if (!"test-redmine-key".equals(exchange.getRequestHeaders().getFirst("X-Redmine-API-Key"))) {
            write(exchange, 401, "{\"errors\":[\"unauthorized\"]}");
            return;
        }
        if (exchange.getRequestURI().getPath().endsWith("/204.json")) {
            write(exchange, 200, """
                    {
                      "issue": {
                        "id": 204,
                        "project": {"id": 12, "name": "Proyecto principal"},
                        "subject": "Fallo de integración",
                        "description": "Detalle",
                        "status": {"id": 5, "name": "Cerrado", "is_closed": true},
                        "assigned_to": {"name": "Ana"},
                        "updated_on": "2026-10-02T11:00:00Z",
                        "custom_fields": [{"id": 1, "name": "Prioridad", "value": "Alta"}],
                        "journals": [
                          {"id": 1, "user": {"name": "Ana"}, "notes": "Se cerró", "created_on": "2026-10-02T12:00:00Z", "details": [{"property": "status_id", "old_value": "1", "new_value": "5"}]},
                          {"id": 2, "user": {"name": "Luis"}, "notes": "Investigación", "created_on": "2026-10-01T12:00:00Z", "details": [{"property": "assigned_to_id", "old_value": "7", "new_value": "9"}]}
                        ]
                      }
                    }
                    """);
            return;
        }
        write(exchange, 404, "{\"error\":\"Not Found\"}");
    }

    private static void write(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
