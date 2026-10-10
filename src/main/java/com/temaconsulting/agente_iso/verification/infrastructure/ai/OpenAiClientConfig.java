package com.temaconsulting.agente_iso.verification.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.temaconsulting.agente_iso.verification.application.AiApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.ApplicabilityAiPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OpenAiClientConfig {
    @Bean
    ApplicabilityAiPort applicabilityAiPort(
            @Value("${tema.openai.enabled:false}") boolean enabled,
            @Value("${tema.openai.api-key:}") String apiKey,
            @Value("${tema.openai.model:}") String model,
            @Value("${tema.openai.timeout:PT20S}") Duration timeout
    ) {
        if (!enabled || apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            return request -> AiApplicabilityAssessment.undetermined(request.expectationId(),
                    "No fue posible determinar automáticamente si corresponde exigir esta evidencia.",
                    "La integración de OpenAI no está configurada.", null);
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("tema.openai.timeout must be between PT0.001S and PT60S");
        }
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(1)
                .build();
        return new OpenAiApplicabilityAdapter(client, model, new ObjectMapper());
    }
}
