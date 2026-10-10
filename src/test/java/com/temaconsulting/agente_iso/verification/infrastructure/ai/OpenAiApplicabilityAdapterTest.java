package com.temaconsulting.agente_iso.verification.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputItem;
import com.openai.models.responses.StructuredResponseOutputMessage;
import com.openai.services.blocking.ResponseService;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.verification.application.AiApplicabilityRequest;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiApplicabilityAdapterTest {
    private static final UUID EXPECTATION_ID = UUID.fromString("5cd8e8be-9db9-43a8-ab08-53c8ae9bb1f6");
    private final OpenAIClient client = mock(OpenAIClient.class);
    private final ResponseService responses = mock(ResponseService.class);
    private final OpenAiApplicabilityAdapter adapter = new OpenAiApplicabilityAdapter(client,
            "test-structured-model", new ObjectMapper());

    @BeforeEach
    void setup() {
        when(client.responses()).thenReturn(responses);
    }

    @Test
    void acceptedProposalUsesOnlyReferencedFactsAndDoesNotStoreResponse() {
        OpenAiApplicabilityAdapter.ModelAnswer answer = answer("APPLIES", List.of("ticket:204:description"));
        var mockResponse = response(answer);
        when(responses.create(any(StructuredResponseCreateParams.class))).thenReturn(mockResponse);

        var assessment = adapter.assess(request());

        assertThat(assessment.applicability()).isEqualTo(ApplicabilityDecision.APPLIES);
        assertThat(assessment.evidenceRefs()).containsExactly("ticket:204:description");
        assertThat(assessment.expectationId()).isEqualTo(EXPECTATION_ID);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<StructuredResponseCreateParams> captor =
                ArgumentCaptor.forClass(StructuredResponseCreateParams.class);
        verify(responses).create(captor.capture());
        ResponseCreateParams params = captor.getValue().rawParams();
        assertThat(params.store()).contains(false);
        assertThat(params.model().orElseThrow().toString()).contains("test-structured-model");
        assertThat(params.input().orElseThrow().toString()).contains("ticket:204:description");
        assertThat(params.instructions().orElseThrow()).contains("no selecciones documentos");
    }

    @Test
    void inventedReferenceIsRejectedAfterOneLimitedRetry() {
        var mockResponse = response(answer("NOT_APPLICABLE", List.of("ticket:999:description")));
        when(responses.create(any(StructuredResponseCreateParams.class)))
                .thenReturn(mockResponse);

        var assessment = adapter.assess(request());

        assertThat(assessment.applicability()).isEqualTo(ApplicabilityDecision.UNDETERMINED);
        assertThat(assessment.limitations()).singleElement().asString().contains("dos intentos");
        verify(responses, org.mockito.Mockito.times(2)).create(any(StructuredResponseCreateParams.class));
    }

    @Test
    void wrongExpectationIdCannotBeUsed() {
        var answer = answer("APPLIES", List.of("ticket:204:description"));
        answer.expectationId = UUID.randomUUID().toString();
        var mockResponse = response(answer);
        when(responses.create(any(StructuredResponseCreateParams.class))).thenReturn(mockResponse);

        assertThat(adapter.assess(request()).applicability()).isEqualTo(ApplicabilityDecision.UNDETERMINED);
    }

    @Test
    void missingFactsNeverCallsOpenAi() {
        var request = new AiApplicabilityRequest(EXPECTATION_ID, "Orden de transporte",
                ApplicabilityMode.CASE_DEPENDENT, "SOPORTE", List.of(), List.of(), List.of());

        assertThat(adapter.assess(request).applicability()).isEqualTo(ApplicabilityDecision.UNDETERMINED);
        verify(responses, never()).create(any(StructuredResponseCreateParams.class));
    }

    @Test
    void alwaysApplicableExpectationCannotEnterAiPort() {
        assertThatThrownBy(() -> new AiApplicabilityRequest(EXPECTATION_ID, "Highlights",
                ApplicabilityMode.ALWAYS, "SOPORTE", List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AiApplicabilityRequest request() {
        return new AiApplicabilityRequest(EXPECTATION_ID,
                "Orden de transporte (en casos de desarrollo, configuración o corrección)",
                ApplicabilityMode.CASE_DEPENDENT, "SOPORTE", List.of("Si corresponde"),
                List.of("Resolver el requerimiento"),
                List.of(new AiApplicabilityRequest.Fact("ticket:204:description", "Descripción del #204",
                        "Se realizó una corrección de configuración")));
    }

    private static OpenAiApplicabilityAdapter.ModelAnswer answer(String decision, List<String> refs) {
        var answer = new OpenAiApplicabilityAdapter.ModelAnswer();
        answer.expectationId = EXPECTATION_ID.toString();
        answer.applicability = decision;
        answer.reason = "El ticket describe una corrección de configuración.";
        answer.evidenceRefs = refs;
        answer.limitations = List.of();
        return answer;
    }

    @SuppressWarnings("unchecked")
    private static StructuredResponse<OpenAiApplicabilityAdapter.ModelAnswer> response(
            OpenAiApplicabilityAdapter.ModelAnswer answer
    ) {
        StructuredResponse<OpenAiApplicabilityAdapter.ModelAnswer> response = mock(StructuredResponse.class);
        StructuredResponseOutputItem<OpenAiApplicabilityAdapter.ModelAnswer> item = mock(StructuredResponseOutputItem.class);
        StructuredResponseOutputMessage<OpenAiApplicabilityAdapter.ModelAnswer> message =
                mock(StructuredResponseOutputMessage.class);
        StructuredResponseOutputMessage.Content<OpenAiApplicabilityAdapter.ModelAnswer> content =
                mock(StructuredResponseOutputMessage.Content.class);
        when(response.error()).thenReturn(Optional.empty());
        when(response.incompleteDetails()).thenReturn(Optional.empty());
        when(response.output()).thenReturn(List.of(item));
        when(response.id()).thenReturn("resp-test");
        when(item.message()).thenReturn(Optional.of(message));
        when(message.content()).thenReturn(List.of(content));
        when(content.outputText()).thenReturn(Optional.of(answer));
        return response;
    }
}
