package com.temaconsulting.agente_iso.integration.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.Cardinality;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.Kind;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.Outcome;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.Problem;
import com.temaconsulting.agente_iso.integration.application.DriveEvidenceSearch.SearchRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleDriveEvidenceSearchTest {
    private HttpServer server;
    private final Map<String, String> pages = new HashMap<>();
    private final List<String> queries = new ArrayList<>();
    private int responseStatus = 200;
    private boolean slow;

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
    void findsExactDocumentInNestedFolderAndSupportsRelativePath() {
        pages.put("root|", """
                {"files":[
                  {"id":"nested","name":"Documentos","mimeType":"application/vnd.google-apps.folder","parents":["root"]},
                  {"id":"other","name":"Highlights.xls.bak","mimeType":"application/pdf","parents":["root"]}
                ]}
                """);
        pages.put("nested|", """
                {"files":[{"id":"highlight","name":"Highlights.xls","mimeType":"application/vnd.ms-excel",
                  "parents":["nested"],"webViewLink":"https://drive.google.com/file/d/highlight/view"}]}
                """);
        pages.put("outside|", """
                {"files":[{"id":"wrong","name":"Highlights.xls","mimeType":"application/pdf","parents":["outside"]}]}
                """);

        var byName = adapter(true).search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE));
        var byPath = adapter(true).search(request(Kind.DOCUMENT, "Documentos/Highlights.xls", Cardinality.ONE));
        var wrongPath = adapter(true).search(request(Kind.DOCUMENT, "Otra/Highlights.xls", Cardinality.ONE));

        assertThat(byName.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(byName.matches()).singleElement().satisfies(match -> {
            assertThat(match.externalId()).isEqualTo("highlight");
            assertThat(match.relativePath()).isEqualTo("Documentos/Highlights.xls");
            assertThat(match.webUrl()).contains("highlight/view");
        });
        assertThat(byPath.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(wrongPath.outcome()).isEqualTo(Outcome.ABSENT);
        assertThat(queries).noneMatch(query -> query.contains("'outside' in parents"));
        assertThat(queries).allMatch(query -> query.contains("trashed = false"));
    }

    @Test
    void ignoresWrongTypeTrashedShortcutAndOutOfScopeMetadata() {
        pages.put("root|", """
                {"files":[
                  {"id":"folder","name":"Highlights.xls","mimeType":"application/vnd.google-apps.folder","parents":["root"]},
                  {"id":"trash","name":"Highlights.xls","mimeType":"application/pdf","parents":["root"],"trashed":true},
                  {"id":"shortcut","name":"Highlights.xls","mimeType":"application/vnd.google-apps.shortcut","parents":["root"]},
                  {"id":"outside","name":"Highlights.xls","mimeType":"application/pdf","parents":["another-root"]}
                ]}
                """);

        assertThat(adapter(true).search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE)).outcome())
                .isEqualTo(Outcome.ABSENT);
        var folder = adapter(true).search(request(Kind.FOLDER, "Highlights.xls", Cardinality.ONE));
        assertThat(folder.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(folder.matches()).singleElement().extracting(DriveEvidenceSearch.Match::externalId)
                .isEqualTo("folder");
    }

    @Test
    void readsEveryPageBeforeReportingAbsenceOrAMatch() {
        pages.put("root|", """
                {"files":[],"nextPageToken":"next"}
                """);
        pages.put("root|next", """
                {"files":[{"id":"highlight","name":"Highlights.xls","mimeType":"application/pdf","parents":["root"]}]}
                """);

        var found = adapter(true).search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE));
        var absent = adapter(true).search(request(Kind.DOCUMENT, "Missing.xls", Cardinality.ONE));

        assertThat(found.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(absent.outcome()).isEqualTo(Outcome.ABSENT);
        assertThat(queries).anyMatch(query -> query.contains("pageToken=next"));
    }

    @Test
    void distinguishesAmbiguousOneFromAllowedMultiple() {
        pages.put("root|", """
                {"files":[
                  {"id":"one","name":"Orden de transporte.pdf","mimeType":"application/pdf","parents":["root"]},
                  {"id":"two","name":"Orden de transporte.pdf","mimeType":"application/pdf","parents":["root"]}
                ]}
                """);

        var one = adapter(true).search(request(Kind.DOCUMENT, "Orden de transporte.pdf", Cardinality.ONE));
        var multiple = adapter(true).search(request(Kind.DOCUMENT, "Orden de transporte.pdf", Cardinality.MULTIPLE));

        assertThat(one.outcome()).isEqualTo(Outcome.AMBIGUOUS);
        assertThat(one.matches()).hasSize(2);
        assertThat(multiple.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(multiple.matches()).hasSize(2);
    }

    @Test
    void incompleteReadNeverBecomesAbsenceEvenIfEarlierPageMatched() {
        pages.put("root|", """
                {"files":[{"id":"one","name":"Highlights.xls","mimeType":"application/pdf","parents":["root"]}],
                 "nextPageToken":"next"}
                """);
        pages.put("root|next", """
                {"files":[],"incompleteSearch":true}
                """);

        var result = adapter(true).search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CHECKABLE);
        assertThat(result.problem()).isEqualTo(Problem.INCOMPLETE);
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void repeatedPageTokenAndTraversalLimitCannotProveAbsence() {
        pages.put("root|", """
                {"files":[],"nextPageToken":"next"}
                """);
        pages.put("root|next", """
                {"files":[],"nextPageToken":"next"}
                """);
        assertThat(adapter(true).search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE)).problem())
                .isEqualTo(Problem.INCOMPLETE);

        StringBuilder folders = new StringBuilder("{\"files\":[");
        for (int i = 0; i < 1001; i++) {
            if (i > 0) folders.append(',');
            folders.append("{\"id\":\"folder-").append(i)
                    .append("\",\"name\":\"Folder\",\"mimeType\":\"application/vnd.google-apps.folder\",\"parents\":[\"root\"]}");
        }
        pages.put("root|", folders.append("]}").toString());
        assertThat(adapter(true).search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE)).problem())
                .isEqualTo(Problem.INCOMPLETE);
    }

    @Test
    void invalidNomenclatureAndMissingRootAreNotDocumentAbsence() {
        assertThat(adapter(true).search(request(Kind.DOCUMENT, "../Highlights.xls", Cardinality.ONE)).problem())
                .isEqualTo(Problem.INVALID_EXPECTATION);
        assertThat(adapter(true).search(request(Kind.DOCUMENT, "", Cardinality.ONE)).problem())
                .isEqualTo(Problem.INVALID_EXPECTATION);
        assertThat(adapter(true).search(new SearchRequest("missing", Kind.DOCUMENT,
                "Highlights.xls", Cardinality.ONE)).problem()).isEqualTo(Problem.MISSING_FOLDER);
        assertThat(queries).isEmpty();
    }

    @Test
    void remoteFailuresAndMissingConfigurationAreNotReportedAsAbsence() {
        var disabled = adapter(false).search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE));
        assertThat(disabled.problem()).isEqualTo(Problem.NOT_CONFIGURED);
        assertThat(queries).isEmpty();

        responseStatus = 401;
        assertThat(adapter(true).search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE)).problem())
                .isEqualTo(Problem.AUTH_ERROR);
        responseStatus = 403;
        assertThat(adapter(true).search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE)).problem())
                .isEqualTo(Problem.PERMISSION_ERROR);
        responseStatus = 200;
        slow = true;
        assertThat(adapter(true, "PT0.01S").search(request(Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE)).problem())
                .isEqualTo(Problem.UNAVAILABLE);
        assertThat(adapter(true).search(new SearchRequest(null, Kind.DOCUMENT, "Highlights.xls", Cardinality.ONE))
                .problem()).isEqualTo(Problem.MISSING_FOLDER);
    }

    private static SearchRequest request(Kind kind, String nomenclature, Cardinality cardinality) {
        return new SearchRequest("root", kind, nomenclature, cardinality);
    }

    private GoogleDriveAdapter adapter(boolean enabled) {
        return adapter(enabled, "PT1S");
    }

    private GoogleDriveAdapter adapter(boolean enabled, String timeout) {
        return new GoogleDriveAdapter(RestClient.builder(), () -> "test-access-token", enabled,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/drive/v3", timeout);
    }

    private void respond(HttpExchange exchange) throws IOException {
        if (slow) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        if (responseStatus != 200) {
            write(exchange, responseStatus, "{}");
            return;
        }
        if (!"Bearer test-access-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            write(exchange, 401, "{}");
            return;
        }
        if (exchange.getRequestURI().getPath().endsWith("/root")) {
            write(exchange, 200, """
                    {"id":"root","name":"Servicio","mimeType":"application/vnd.google-apps.folder","trashed":false}
                    """);
            return;
        }
        if (exchange.getRequestURI().getPath().endsWith("/missing")) {
            write(exchange, 404, "{}");
            return;
        }
        String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
        queries.add(query);
        String parent = query.contains("'nested' in parents") ? "nested"
                : query.contains("'folder' in parents") ? "folder"
                : query.contains("'outside' in parents") ? "outside" : "root";
        String pageToken = query.contains("pageToken=next") ? "next" : "";
        write(exchange, 200, pages.getOrDefault(parent + "|" + pageToken, "{\"files\":[]}"));
    }

    private static void write(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        } catch (IOException ignored) {
            // A timeout can close the test client before the fake server writes its response.
        }
    }
}
