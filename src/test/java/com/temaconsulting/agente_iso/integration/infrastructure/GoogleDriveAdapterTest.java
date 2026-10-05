package com.temaconsulting.agente_iso.integration.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.temaconsulting.agente_iso.integration.application.SourceProbeException;
import com.temaconsulting.agente_iso.integration.domain.SourceHealth;
import com.temaconsulting.agente_iso.shared.application.ApiException;
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

class GoogleDriveAdapterTest {
    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private int responseStatus = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/drive/v3/files", this::respond);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void disabledConnectorStartsWithoutCredentialsAndDoesNotRead() {
        GoogleDriveAdapter drive = adapter(false);
        assertThat(drive.isConfigured()).isFalse();
        assertThatThrownBy(() -> drive.search("", 0, 20))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no está configurada");
        assertThat(requests).isEmpty();
    }

    @Test
    void listsOnlyFoldersAcrossDrivePagesAndReportsRealTotal() {
        GoogleDriveAdapter drive = adapter(true);

        var first = drive.search("Cliente", 0, 1);
        var second = drive.search("Cliente", 1, 1);

        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.items()).hasSize(1);
        assertThat(second.totalElements()).isEqualTo(2);
        assertThat(second.items()).hasSize(1);
        assertThat(first.items().getFirst().id()).isNotEqualTo(second.items().getFirst().id());
        assertThat(first.items().getFirst().path()).startsWith("/");
        assertThat(requests).allMatch(request -> request.startsWith("GET "));
        assertThat(requests).anyMatch(request -> request.contains("pageToken=next"));
    }

    @Test
    void resolvesOnlyAnAccessibleActiveFolderById() {
        GoogleDriveAdapter drive = adapter(true);

        assertThat(drive.findById("folder-1").name()).isEqualTo("Cliente Uno");
        assertThat(drive.findById("document-1")).isNull();
        assertThat(drive.findById("missing")).isNull();
        assertThat(requests).allMatch(request -> request.startsWith("GET "));
    }

    @Test
    void probeUsesRealReadAndMapsRemoteAuthenticationFailures() {
        GoogleDriveAdapter drive = adapter(true);
        drive.checkReadable();
        assertThat(requests).hasSize(1);

        responseStatus = 401;
        assertThatThrownBy(drive::checkReadable)
                .isInstanceOfSatisfying(SourceProbeException.class,
                        failure -> assertThat(failure.status()).isEqualTo(SourceHealth.AUTH_ERROR));
        responseStatus = 403;
        assertThatThrownBy(drive::checkReadable)
                .isInstanceOfSatisfying(SourceProbeException.class,
                        failure -> assertThat(failure.status()).isEqualTo(SourceHealth.PERMISSION_ERROR));
    }

    private GoogleDriveAdapter adapter(boolean enabled) {
        return new GoogleDriveAdapter(RestClient.builder(), () -> "test-access-token", enabled,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/drive/v3", "PT1S");
    }

    private void respond(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
        if (!"Bearer test-access-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            write(exchange, 401, "{}");
            return;
        }
        if (responseStatus != 200) {
            write(exchange, responseStatus, "{}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/folder-1")) {
            write(exchange, 200, """
                    {"id":"folder-1","name":"Cliente Uno","mimeType":"application/vnd.google-apps.folder","trashed":false}
                    """);
        } else if (path.endsWith("/document-1")) {
            write(exchange, 200, """
                    {"id":"document-1","name":"Otro","mimeType":"application/pdf","trashed":false}
                    """);
        } else if (path.endsWith("/missing")) {
            write(exchange, 404, "{}");
        } else if (exchange.getRequestURI().getRawQuery().contains("pageToken=next")) {
            write(exchange, 200, """
                    {"files":[{"id":"folder-2","name":"Cliente Dos","mimeType":"application/vnd.google-apps.folder"}]}
                    """);
        } else {
            write(exchange, 200, """
                    {"files":[
                      {"id":"folder-1","name":"Cliente Uno","mimeType":"application/vnd.google-apps.folder"},
                      {"id":"document-1","name":"Cliente documento","mimeType":"application/pdf"}
                    ],"nextPageToken":"next"}
                    """);
        }
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
