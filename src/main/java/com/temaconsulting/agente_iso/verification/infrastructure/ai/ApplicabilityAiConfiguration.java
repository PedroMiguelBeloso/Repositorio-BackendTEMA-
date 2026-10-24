package com.temaconsulting.agente_iso.verification.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.port.ApplicabilityAiPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Locale;

/** Exactly one AI port is active. Missing credentials leave case-dependent results for human review. */
@Configuration
public class ApplicabilityAiConfiguration {
    @Bean
    ApplicabilityAiPort applicabilityAiPort(
            RestClient.Builder restClientBuilder,
            @Value("${tema.ai.provider:GEMINI}") String provider,
            @Value("${tema.gemini.api-key:}") String geminiApiKey,
            @Value("${tema.gemini.model:gemini-2.5-flash}") String geminiModel,
            @Value("${tema.gemini.timeout:PT20S}") Duration geminiTimeout,
            @Value("${tema.gemini.api-base-url:https://generativelanguage.googleapis.com}") String geminiBaseUrl,
            @Value("${tema.openai.enabled:false}") boolean openAiEnabled,
            @Value("${tema.openai.api-key:}") String openAiApiKey,
            @Value("${tema.openai.model:}") String openAiModel,
            @Value("${tema.openai.timeout:PT20S}") Duration openAiTimeout
    ) {
        return switch (provider.trim().toUpperCase(Locale.ROOT)) {
            case "GEMINI" -> gemini(restClientBuilder, new ObjectMapper(), geminiApiKey, geminiModel,
                    geminiTimeout, geminiBaseUrl);
            case "OPENAI" -> openAi(openAiEnabled, openAiApiKey, openAiModel, openAiTimeout,
                    new ObjectMapper());
            case "NONE" -> unavailable("La interpretación automática está deshabilitada.");
            default -> throw new IllegalArgumentException("tema.ai.provider must be GEMINI, OPENAI or NONE");
        };
    }

    private static ApplicabilityAiPort gemini(RestClient.Builder builder, ObjectMapper mapper,
                                              String key, String model, Duration timeout, String baseUrl) {
        if (key == null || key.isBlank()) {
            return unavailable("La integración de Gemini no está configurada.");
        }
        validateTimeout(timeout, "tema.gemini.timeout");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("tema.gemini.api-base-url is required");
        }
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(timeout);
        requests.setReadTimeout(timeout);
        RestClient client = builder.clone().baseUrl(baseUrl.trim())
                .defaultHeader("x-goog-api-key", key.trim())
                .requestFactory(requests).build();
        return new GeminiApplicabilityAdapter(client, model, mapper);
    }

    private static ApplicabilityAiPort openAi(boolean enabled, String key, String model,
                                              Duration timeout, ObjectMapper mapper) {
        if (!enabled || key == null || key.isBlank() || model == null || model.isBlank()) {
            return unavailable("La integración de OpenAI no está configurada.");
        }
        validateTimeout(timeout, "tema.openai.timeout");
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(key).timeout(timeout).maxRetries(1).build();
        return new OpenAiApplicabilityAdapter(client, model, mapper);
    }

    private static void validateTimeout(Duration timeout, String property) {
        if (timeout == null || timeout.toMillis() < 1 || timeout.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException(property + " must be between 1 ms and 60 s");
        }
    }

    private static ApplicabilityAiPort unavailable(String limitation) {
        return request -> AiApplicabilityAssessment.undetermined(request.expectationId(),
                "No fue posible determinar automáticamente si corresponde exigir esta evidencia.",
                limitation, null);
    }
}
