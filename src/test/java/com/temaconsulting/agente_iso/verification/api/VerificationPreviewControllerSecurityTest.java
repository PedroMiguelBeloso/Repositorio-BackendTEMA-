package com.temaconsulting.agente_iso.verification.api;

import com.temaconsulting.agente_iso.identity.infrastructure.JsonAccessDeniedHandler;
import com.temaconsulting.agente_iso.identity.infrastructure.JsonAuthenticationEntryPoint;
import com.temaconsulting.agente_iso.identity.infrastructure.JwtRoleConverter;
import com.temaconsulting.agente_iso.identity.infrastructure.SecurityConfig;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import com.temaconsulting.agente_iso.shared.api.ApiExceptionHandler;
import com.temaconsulting.agente_iso.shared.api.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.config.Configuration;
import com.temaconsulting.agente_iso.verification.application.VerificationPreviewService;
import com.temaconsulting.agente_iso.verification.application.VerificationPreviewFailure;
import com.temaconsulting.agente_iso.verification.domain.PreviewExpectation;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VerificationPreviewController.class)
@Import({
        SecurityConfig.class,
        JwtRoleConverter.class,
        JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class,
        ApiExceptionHandler.class,
        VerificationApiExceptionHandler.class,
        ProblemDetailsFactory.class,
        Configuration.class
})
class VerificationPreviewControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    VerificationPreviewService previews;

    @Test
    void adminCanRequestPreview() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        UUID expectationId = UUID.randomUUID();
        when(previews.preview(any())).thenReturn(new VerificationPreview(
                serviceId,
                5,
                versionId,
                VerificationScope.MACRO_ACTIVITY,
                macroId,
                List.of(new PreviewExpectation(
                        expectationId,
                        macroId,
                        null,
                        EvidenceLocation.INPUT,
                        "Ticket",
                        EvidenceType.TICKET,
                        ApplicabilityMode.ALWAYS,
                        Cardinality.ONE,
                        "ticket-group:" + macroId
                )),
                List.of(TicketSelectionGroup.forMacroActivity(
                        macroId,
                        "Tickets",
                        Cardinality.ONE,
                        List.of(expectationId),
                        List.of(expectationId),
                        true
                ))
        ));

        mockMvc.perform(post("/api/v1/verifications/preview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"serviceId\":\"" + serviceId + "\"," +
                                "\"processVersionId\":\"" + versionId + "\"," +
                                "\"scope\":\"MACRO_ACTIVITY\",\"macroActivityId\":\"" + macroId + "\"" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.serviceId").value(serviceId.toString()))
                .andExpect(jsonPath("$.serviceRevision").value(5))
                .andExpect(jsonPath("$.processVersionId").value(versionId.toString()))
                .andExpect(jsonPath("$.scope").value("MACRO_ACTIVITY"))
                .andExpect(jsonPath("$.macroActivityId").value(macroId.toString()))
                .andExpect(jsonPath("$.expectations[0].id").value(expectationId.toString()));
    }

    @Test
    void adminGetsBadRequestWhenMacroActivityIdShapeIsInvalid() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/verifications/preview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"serviceId\":\"" + serviceId + "\"," +
                                "\"processVersionId\":\"" + versionId + "\"," +
                                "\"scope\":\"MACRO_ACTIVITY\"" +
                                "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("macroActivityId"));
    }

    @Test
    void adminGetsBadRequestWhenProcessScopeContainsMacroActivityId() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/verifications/preview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"serviceId\":\"" + serviceId + "\"," +
                                "\"processVersionId\":\"" + versionId + "\"," +
                                "\"scope\":\"PROCESS\"," +
                                "\"macroActivityId\":\"" + macroId + "\"" +
                                "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("macroActivityId"));
    }

    @Test
    void applicationNotFoundFailureIsTranslatedByVerificationApi() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(previews.preview(any())).thenThrow(
                VerificationPreviewFailure.missing("Service not found: " + serviceId));

        mockMvc.perform(post("/api/v1/verifications/preview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"" + serviceId + "\",\"processVersionId\":\"" + versionId + "\",\"scope\":\"PROCESS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void applicationTicketRuleFailureIsTranslatedAsUnprocessableEntity() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(previews.preview(any())).thenThrow(
                VerificationPreviewFailure.businessRule(
                        com.temaconsulting.agente_iso.shared.application.ProblemCode.TICKET_SELECTION_INVALID,
                        "macroActivityId",
                        "Incompatible ticket cardinality"));

        mockMvc.perform(post("/api/v1/verifications/preview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"" + serviceId + "\",\"processVersionId\":\"" + versionId + "\",\"scope\":\"PROCESS\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TICKET_SELECTION_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value("macroActivityId"));
    }

    @Test
    void readerGetsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/verifications/preview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousGetsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/verifications/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
