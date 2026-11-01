package com.temaconsulting.agente_iso.verification.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityRequest;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import org.slf4j.LoggerFactory;

class GeminiApplicabilityAdapterTest {
    private static final UUID EXPECTATION_ID = UUID.fromString("5cd8e8be-9db9-43a8-ab08-53c8ae9bb1f6");
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsOnlyCuratedContextAndAcceptsReferencedProposal() throws IOException {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = server(exchange -> {
            path.set(exchange.getRequestURI().toString());
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 200, geminiAnswer("APPLIES", "ticket:204:description"));
        });

        var assessment = adapter().assess(request());

        assertThat(assessment.applicability()).isEqualTo(ApplicabilityDecision.APPLIES);
        assertThat(assessment.evidenceRefs()).containsExactly("ticket:204:description");
        assertThat(assessment.model()).isEqualTo("gemini-2.5-flash");
        assertThat(assessment.responseId()).isEqualTo("response-test");
        assertThat(path.get()).isEqualTo("/v1beta/models/gemini-2.5-flash:generateContent");
        assertThat(key.get()).isEqualTo("test-key");
        assertThat(body.get()).contains("ticket:204:description", "responseMimeType", "application/json",
                        "responseJsonSchema", "additionalProperties")
                .doesNotContain("test-key");
    }

    @Test
    void currentModelUsesStructuredOutputWithoutLegacySamplingSettings() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        server = server(exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 200, geminiAnswer("APPLIES", "ticket:204:description"));
        });

        var assessment = adapter("gemini-3.5-flash-lite").assess(request());

        assertThat(assessment.applicability()).isEqualTo(ApplicabilityDecision.APPLIES);
        assertThat(body.get()).contains("responseFormat", "APPLICATION_JSON", "schema",
                        "APPLIES", "NOT_APPLICABLE", "UNDETERMINED")
                .doesNotContain("thinkingBudget", "temperature", "responseMimeType");
    }

    @Test
    void acceptsAnswerAfterSeparateThinkingPart() throws IOException {
        String answer = "{\"expectationId\":\"" + EXPECTATION_ID + "\",\"applicability\":\"APPLIES\","
                + "\"reason\":\"El ticket describe una corrección.\","
                + "\"evidenceRefs\":[\"ticket:204:description\"],\"limitations\":[]}";
        server = server(exchange -> reply(exchange, 200,
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":["
                        + "{\"thought\":true,\"text\":\"internal text\"},"
                        + "{\"text\":" + json(answer) + "}]}}]}"));

        assertThat(adapter("gemini-3.5-flash-lite").assess(request()).applicability())
                .isEqualTo(ApplicabilityDecision.APPLIES);
    }

    @Test
    void inventedReferenceIsRejectedAfterOneLimitedRetry() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> {
            calls.incrementAndGet();
            reply(exchange, 200, geminiAnswer("APPLIES", "ticket:999:description"));
        });

        var assessment = adapter().assess(request());

        assertThat(assessment.applicability()).isEqualTo(ApplicabilityDecision.UNDETERMINED);
        assertThat(assessment.limitations()).singleElement().asString().contains("dos intentos");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void authenticationErrorDoesNotExposeResponseOrRetry() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> {
            calls.incrementAndGet();
            reply(exchange, 401, "{\"error\":\"secret test-key\"}");
        });

        var assessment = adapter().assess(request());

        assertThat(assessment.applicability()).isEqualTo(ApplicabilityDecision.UNDETERMINED);
        assertThat(assessment.limitations()).singleElement().asString()
                .contains("autenticación").doesNotContain("test-key");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void transientServerFailureIsRetriedOnce() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> {
            if (calls.incrementAndGet() == 1) {
                reply(exchange, 503, "{\"error\":\"temporarily unavailable\"}");
            } else {
                reply(exchange, 200, geminiAnswer("APPLIES", "ticket:204:description"));
            }
        });

        var assessment = adapter().assess(request());

        assertThat(assessment.applicability()).isEqualTo(ApplicabilityDecision.APPLIES);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void emptyFactsNeverCallGemini() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> {
            calls.incrementAndGet();
            reply(exchange, 200, geminiAnswer("APPLIES", "ticket:204:description"));
        });
        var request = new AiApplicabilityRequest(EXPECTATION_ID, "Orden de transporte",
                ApplicabilityMode.CASE_DEPENDENT, "SOPORTE", List.of(), List.of(), List.of());

        assertThat(adapter().assess(request).applicability()).isEqualTo(ApplicabilityDecision.UNDETERMINED);
        assertThat(calls.get()).isZero();
    }

    @Test
    void truncatedOutputCannotProduceAnApplicableDecision() throws IOException {
        server = server(exchange -> reply(exchange, 200, """
                {"responseId":"response-test","candidates":[{"finishReason":"MAX_TOKENS",
                "content":{"parts":[{"text":"{}"}]}}]}
                """));

        assertThat(adapter().assess(request()).applicability()).isEqualTo(ApplicabilityDecision.UNDETERMINED);
    }

    @Test
    void logsOnlySafeRejectionCategoryForTruncatedOutput() throws IOException {
        server = server(exchange -> reply(exchange, 200, """
                {"candidates":[{"finishReason":"MAX_TOKENS",
                "content":{"parts":[{"text":"sensitive-content"}]}}]}
                """));
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiApplicabilityAdapter.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            adapter().assess(request());
            assertThat(appender.list).hasSize(2);
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("reason=MAX_TOKENS")
                        .doesNotContain("sensitive-content", "test-key");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private GeminiApplicabilityAdapter adapter() {
        return adapter("gemini-2.5-flash");
    }

    private GeminiApplicabilityAdapter adapter(String model) {
        RestClient client = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .defaultHeader("x-goog-api-key", "test-key")
                .build();
        return new GeminiApplicabilityAdapter(client, model, new ObjectMapper());
    }

    private static AiApplicabilityRequest request() {
        return new AiApplicabilityRequest(EXPECTATION_ID,
                "Orden de transporte (en casos de desarrollo, configuración o corrección)",
                ApplicabilityMode.CASE_DEPENDENT, "SOPORTE", List.of("Si corresponde"),
                List.of("Resolver el requerimiento"),
                List.of(new AiApplicabilityRequest.Fact("ticket:204:description", "Descripción del #204",
                        "Se realizó una corrección de configuración")));
    }

    private static String geminiAnswer(String decision, String ref) {
        String answer = "{\"expectationId\":\"" + EXPECTATION_ID + "\",\"applicability\":\"" + decision
                + "\",\"reason\":\"El ticket describe una corrección.\",\"evidenceRefs\":[\"" + ref
                + "\"],\"limitations\":[]}";
        return "{\"responseId\":\"response-test\",\"candidates\":[{\"finishReason\":\"STOP\","
                + "\"content\":{\"parts\":[{\"text\":" + json(answer) + "}]}}]}";
    }

    private static String json(String value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
