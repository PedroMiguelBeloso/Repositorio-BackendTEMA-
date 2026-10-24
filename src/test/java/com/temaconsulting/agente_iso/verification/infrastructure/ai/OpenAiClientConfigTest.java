package com.temaconsulting.agente_iso.verification.infrastructure.ai;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityRequest;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientConfigTest {
    @Test
    void backendCanBootWithoutApiCredentialsAndDoesNotInventAvailability() {
        var port = new OpenAiClientConfig().applicabilityAiPort(false, "", "", Duration.ofSeconds(20));
        var request = new AiApplicabilityRequest(UUID.randomUUID(), "Orden de transporte",
                ApplicabilityMode.CASE_DEPENDENT, "SOPORTE", List.of(), List.of(), List.of());

        var assessment = port.assess(request);

        assertThat(assessment.applicability()).isEqualTo(ApplicabilityDecision.UNDETERMINED);
        assertThat(assessment.limitations()).singleElement().asString().contains("no está configurada");
    }
}
