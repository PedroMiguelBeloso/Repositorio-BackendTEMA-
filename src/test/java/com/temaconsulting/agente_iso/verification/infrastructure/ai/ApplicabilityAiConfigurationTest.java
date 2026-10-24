package com.temaconsulting.agente_iso.verification.infrastructure.ai;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityRequest;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicabilityAiConfigurationTest {
    private final ApplicabilityAiConfiguration config = new ApplicabilityAiConfiguration();

    @Test
    void missingGeminiKeyLeavesAssessmentForHumanReview() {
        var port = create("GEMINI", "", "");
        var request = new AiApplicabilityRequest(UUID.randomUUID(), "Orden de transporte",
                ApplicabilityMode.CASE_DEPENDENT, "SOPORTE", List.of(), List.of(), List.of());

        var assessment = port.assess(request);

        assertThat(assessment.applicability()).isEqualTo(ApplicabilityDecision.UNDETERMINED);
        assertThat(assessment.limitations()).singleElement().asString().contains("Gemini no está configurada");
    }

    @Test
    void geminiIsSelectedWhenKeyExists() {
        assertThat(create("GEMINI", "test-key", "")).isInstanceOf(GeminiApplicabilityAdapter.class);
    }

    @Test
    void openAiCanStillBeSelectedExplicitly() {
        assertThat(create("OPENAI", "", "test-openai-key"))
                .isInstanceOf(OpenAiApplicabilityAdapter.class);
    }

    @Test
    void unsupportedProviderFailsAtStartup() {
        assertThatThrownBy(() -> create("OTHER", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private com.temaconsulting.agente_iso.verification.application.port.ApplicabilityAiPort create(
            String provider, String geminiKey, String openAiKey) {
        return config.applicabilityAiPort(RestClient.builder(), provider,
                geminiKey, "gemini-2.5-flash", Duration.ofSeconds(20),
                "https://generativelanguage.googleapis.com", true,
                openAiKey, "gpt-4.1-mini", Duration.ofSeconds(20));
    }
}
