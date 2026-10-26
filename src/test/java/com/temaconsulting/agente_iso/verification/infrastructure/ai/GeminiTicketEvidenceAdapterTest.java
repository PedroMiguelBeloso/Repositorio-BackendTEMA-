package com.temaconsulting.agente_iso.verification.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiTicketEvidenceAdapterTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void sendsCustomFieldsAndJournalsAndAcceptsCitedProposal() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String answer = """
                    {"responseId":"r1","candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\"status\\":\\"COMPLIES\\",\\"reason\\":\\"La solución fue validada en el campo personalizado.\\",\\"citedFields\\":[\\"customFields\\"],\\"limitations\\":[]}"}]}}]}
                    """;
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        var adapter = new GeminiTicketEvidenceAdapter(RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build(),
                "gemini-2.5-flash", new ObjectMapper());
        var ticket = new TicketSnapshot("42", "9", "TEMA", "Entrega", "Closed", "5", "Closed",
                true, "Solución", null, Instant.parse("2026-10-26T12:00:00Z"),
                Instant.parse("2026-10-25T12:00:00Z"),
                List.of(new TicketSnapshot.CustomField("7", "Validación de solución", "Aprobada")),
                List.of(new TicketSnapshot.JournalEntry("1", "PM", "Validada",
                        Instant.parse("2026-10-26T11:00:00Z"), List.of())));

        var result = adapter.assess("Ticket cerrado con solución validada", "Ticket", null,
                List.of(), List.of(), ticket);
        assertThat(result.status()).isEqualTo(ResultStatus.COMPLIES);
        assertThat(result.citedFields()).containsExactly("customFields");
        assertThat(body.get()).contains("Validación de solución", "Aprobada", "journals")
                .doesNotContain("x-goog-api-key");
    }
}
