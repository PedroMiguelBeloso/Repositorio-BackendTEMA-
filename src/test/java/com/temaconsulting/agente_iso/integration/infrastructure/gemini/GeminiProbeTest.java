package com.temaconsulting.agente_iso.integration.infrastructure.gemini;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.temaconsulting.agente_iso.integration.application.error.SourceProbeException;
import com.temaconsulting.agente_iso.integration.domain.SourceHealth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiProbeTest {
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private int status = 200;
    private String response = """
            {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"OK"}]}}]}
            """;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/test-model:generateContent", this::respond);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void realMinimalGenerationIsNeededBeforeReportingAvailable() {
        GeminiProbe probe = probe("GEMINI", "test-key");
        assertThat(probe.isConfigured()).isTrue();
        probe.checkReadable();
        assertThat(calls).hasValue(1);
    }

    @Test
    void missingKeyOrDisabledProviderDoesNotCallGemini() {
        assertThat(probe("GEMINI", "").isConfigured()).isFalse();
        assertThat(probe("NONE", "test-key").isConfigured()).isFalse();
        assertThatThrownBy(() -> probe("GEMINI", "").checkReadable())
                .isInstanceOf(SourceProbeException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void classifiesRemoteErrorsWithoutReportingAvailability() {
        for (int httpStatus : new int[]{401, 403, 404, 429, 503}) {
            status = httpStatus;
            SourceHealth expected = httpStatus == 401 ? SourceHealth.AUTH_ERROR
                    : httpStatus == 403 ? SourceHealth.PERMISSION_ERROR : SourceHealth.UNAVAILABLE;
            assertThatThrownBy(() -> probe("GEMINI", "test-key").checkReadable())
                    .isInstanceOfSatisfying(SourceProbeException.class,
                            failure -> assertThat(failure.status()).isEqualTo(expected));
        }
    }

    @Test
    void modelNotFoundHasActionableSafeDiagnostic() {
        status = 404;
        assertThatThrownBy(() -> probe("GEMINI", "test-key").checkReadable())
                .isInstanceOfSatisfying(SourceProbeException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(SourceHealth.UNAVAILABLE);
                    assertThat(failure.safeMessage()).contains("TEMA_GEMINI_MODEL").doesNotContain("test-key");
                });
    }

    @Test
    void malformedOrIncompleteGenerationIsUnavailable() {
        response = "{\"candidates\":[]}";
        assertThatThrownBy(() -> probe("GEMINI", "test-key").checkReadable())
                .isInstanceOfSatisfying(SourceProbeException.class,
                        failure -> assertThat(failure.status()).isEqualTo(SourceHealth.UNAVAILABLE));
    }

    private GeminiProbe probe(String provider, String key) {
        return new GeminiProbe(RestClient.builder(), provider, key,
                "test-model", "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2));
    }

    private void respond(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
