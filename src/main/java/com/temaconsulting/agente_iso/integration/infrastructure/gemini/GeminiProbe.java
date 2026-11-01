package com.temaconsulting.agente_iso.integration.infrastructure.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.temaconsulting.agente_iso.integration.application.error.SourceProbeException;
import com.temaconsulting.agente_iso.integration.application.port.IntegrationProbe;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.SourceHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** An actual, minimal generation request; configured credentials alone never mean AVAILABLE. */
@Component
public class GeminiProbe implements IntegrationProbe {
    private static final Logger log = LoggerFactory.getLogger(GeminiProbe.class);
    private final boolean configured;
    private final String model;
    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiProbe(RestClient.Builder builder,
                       @Value("${tema.ai.provider:GEMINI}") String provider,
                       @Value("${tema.gemini.api-key:}") String key,
                       @Value("${tema.gemini.model:gemini-3.5-flash-lite}") String model,
                       @Value("${tema.gemini.api-base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                       @Value("${tema.integrations.probe-timeout:PT15S}") Duration timeout) {
        this.configured = "GEMINI".equalsIgnoreCase(provider.trim()) && key != null && !key.isBlank();
        this.model = model;
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(timeout);
        requests.setReadTimeout(timeout);
        this.client = builder.clone().baseUrl(baseUrl.trim())
                .defaultHeader("x-goog-api-key", key == null ? "" : key.trim())
                .requestFactory(requests).build();
    }

    @Override
    public IntegrationSource source() {
        return IntegrationSource.GEMINI;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void checkReadable() {
        if (!configured) throw new SourceProbeException(SourceHealth.UNAVAILABLE,
                new IllegalStateException("Gemini is not configured"));
        long started = System.nanoTime();
        try {
            Map<String, Object> generation = model.startsWith("gemini-2.5-flash")
                    ? Map.of("maxOutputTokens", 128, "temperature", 0,
                            "thinkingConfig", Map.of("thinkingBudget", 0))
                    : Map.of("maxOutputTokens", 256);
            String raw = client.post().uri("/v1beta/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "contents", List.of(Map.of("role", "user", "parts",
                                    List.of(Map.of("text", "Responde OK.")))),
                            "generationConfig", generation
                    ))
                    .retrieve().body(String.class);
            JsonNode root = mapper.readTree(raw);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()
                    || !"STOP".equals(candidates.get(0).path("finishReason").asText())
                    || candidates.get(0).path("content").path("parts").path(0).path("text")
                    .asText().isBlank()) {
                throw new SourceProbeException(SourceHealth.UNAVAILABLE,
                        new IllegalStateException("Gemini response was incomplete"));
            }
            log.info("gemini_probe outcome=AVAILABLE durationMs={}", elapsed(started));
        } catch (RestClientResponseException ex) {
            SourceHealth health = switch (ex.getStatusCode().value()) {
                case 401 -> SourceHealth.AUTH_ERROR;
                case 403 -> SourceHealth.PERMISSION_ERROR;
                default -> SourceHealth.UNAVAILABLE;
            };
            log.warn("gemini_probe outcome={} httpStatus={} durationMs={}",
                    health, ex.getStatusCode().value(), elapsed(started));
            String safeMessage = ex.getStatusCode().value() == 404
                    ? "Gemini respondió 404: no se encontró el modelo o recurso solicitado. Revisá TEMA_GEMINI_MODEL y el acceso a Gemini API."
                    : null;
            throw new SourceProbeException(health, ex, safeMessage);
        } catch (RestClientException ex) {
            log.warn("gemini_probe outcome=UNAVAILABLE causeType={} durationMs={}",
                    rootCauseType(ex), elapsed(started));
            throw new SourceProbeException(SourceHealth.UNAVAILABLE, ex);
        } catch (SourceProbeException ex) {
            log.warn("gemini_probe outcome=UNAVAILABLE causeType=INVALID_RESPONSE durationMs={}", elapsed(started));
            throw ex;
        } catch (Exception ex) {
            log.warn("gemini_probe outcome=UNAVAILABLE causeType=INVALID_RESPONSE durationMs={}", elapsed(started));
            throw new SourceProbeException(SourceHealth.UNAVAILABLE, ex);
        }
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static String rootCauseType(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        // Log the exception type only: exception messages may contain request URLs or credentials.
        return cause.getClass().getSimpleName();
    }
}
