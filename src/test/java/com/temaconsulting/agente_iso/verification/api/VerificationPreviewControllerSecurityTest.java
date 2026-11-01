package com.temaconsulting.agente_iso.verification.api;

import com.temaconsulting.agente_iso.verification.api.error.VerificationApiExceptionHandler;

import com.temaconsulting.agente_iso.identity.infrastructure.security.JsonAccessDeniedHandler;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JsonAuthenticationEntryPoint;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JwtRoleConverter;
import com.temaconsulting.agente_iso.identity.infrastructure.security.SecurityConfig;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import com.temaconsulting.agente_iso.shared.api.error.ApiExceptionHandler;
import com.temaconsulting.agente_iso.shared.api.error.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.shared.config.Configuration;
import com.temaconsulting.agente_iso.service.domain.ServiceType;
import com.temaconsulting.agente_iso.verification.application.model.CreatedVerificationRun;
import com.temaconsulting.agente_iso.verification.application.VerificationPreviewService;
import com.temaconsulting.agente_iso.verification.application.error.VerificationPreviewFailure;
import com.temaconsulting.agente_iso.verification.application.VerificationStartService;
import com.temaconsulting.agente_iso.verification.application.VerificationRunDispatchService;
import com.temaconsulting.agente_iso.verification.application.VerificationRunQueryService;
import com.temaconsulting.agente_iso.verification.application.VerificationWorkStatusQueryService;
import com.temaconsulting.agente_iso.verification.api.mapper.VerificationResultApiMapper;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultProgress;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultQueryFailure;
import com.temaconsulting.agente_iso.verification.domain.PreviewExpectation;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelection;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({VerificationPreviewController.class, VerificationRunController.class})
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

    @MockitoBean
    VerificationStartService starts;

    @MockitoBean
    VerificationRunDispatchService dispatcher;

    @MockitoBean
    VerificationRunQueryService queries;

    @MockitoBean
    VerificationWorkStatusQueryService workStatuses;

    @MockitoBean
    VerificationResultApiMapper resultMapper;

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
    void malformedJsonUsesTheVerificationProblemContract() throws Exception {
        mockMvc.perform(post("/api/v1/verifications/preview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("request"));
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
                        com.temaconsulting.agente_iso.shared.application.error.ProblemCode.TICKET_SELECTION_INVALID,
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
    void adminCanStartRunAndReceivesTheValidatedSelections() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        String groupId = TicketSelectionGroup.stableIdFor(macroId);
        ValidatedTicketSelections selected = new ValidatedTicketSelections(List.of(
                new ValidatedTicketSelection(groupId, macroId, List.of("204", "205"))
        ));
        when(starts.start(any(), eq(5), eq(adminId), anyList())).thenReturn(new CreatedVerificationRun(
                runId,
                serviceId,
                ServiceType.SOFTWARE_FACTORY,
                5,
                versionId,
                "drive-folder",
                "Drive",
                "redmine-project",
                "Redmine",
                VerificationScope.PROCESS,
                null,
                null,
                "QUEUED",
                adminId,
                Instant.parse("2026-10-08T12:00:00Z"),
                selected,
                4
        ));

        mockMvc.perform(post("/api/v1/verifications")
                        .with(jwt().jwt(token -> token.subject(adminId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "serviceRevision": 5,
                                  "processVersionId": "%s",
                                  "scope": "PROCESS",
                                  "ticketSelections": [
                                    {"groupId": "%s", "ticketIds": ["204", "205"]}
                                  ]
                                }
                                """.formatted(serviceId, versionId, groupId)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.serviceRevisionSnapshot").value(5))
                .andExpect(jsonPath("$.progress.total").value(4))
                .andExpect(jsonPath("$.progress.processed").value(0))
                .andExpect(jsonPath("$.ticketSelections[0].groupId").value(groupId))
                .andExpect(jsonPath("$.ticketSelections[0].ticketIds[0]").value("204"))
                .andExpect(jsonPath("$.ticketSelections[0].ticketIds[1]").value("205"));
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

    @Test
    void readerCanSeePersistedProgressButAnonymousCannot() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        var run = new VerificationRunSnapshot(runId, serviceId, ServiceType.SOFTWARE_FACTORY, 4,
                versionId, "folder-1", "Folder", "project-1", "Project", VerificationScope.PROCESS,
                null, "case note", "RUNNING", adminId, Instant.parse("2026-10-08T12:00:00Z"),
                null, null, 3, new ValidatedTicketSelections(List.of()));
        when(queries.get(runId)).thenReturn(new VerificationRunQueryService.RunWithProgress(
                run, new VerificationResultProgress(1, 1)));

        mockMvc.perform(get("/api/v1/verifications/{runId}", runId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.progress.total").value(3))
                .andExpect(jsonPath("$.progress.processed").value(1))
                .andExpect(jsonPath("$.progress.reviewRequired").value(1))
                .andExpect(jsonPath("$.sourceSnapshot.driveFolderId").value("folder-1"));
        mockMvc.perform(get("/api/v1/verifications/{runId}", runId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAndReaderCanListRunHistoryButAnonymousCannot() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String groupId = TicketSelectionGroup.stableIdFor(UUID.randomUUID());
        var run = new VerificationRunSnapshot(runId, serviceId, ServiceType.SOFTWARE_FACTORY, 4,
                versionId, "folder-1", "Folder", "project-1", "Project", VerificationScope.PROCESS,
                null, "case note", "FAILED", adminId, Instant.parse("2026-10-08T12:00:00Z"),
                Instant.parse("2026-10-08T12:01:00Z"), "Safe failure", 3,
                new ValidatedTicketSelections(List.of(
                        new ValidatedTicketSelection(groupId,
                                UUID.fromString(groupId.substring("ticket-group:".length())),
                                List.of("204", "205"))
                )));
        when(queries.list(serviceId, 0, 20)).thenReturn(new Page<>(List.of(
                new VerificationRunQueryService.RunWithProgress(run, new VerificationResultProgress(2, 1))
        ), 0, 20, 1));

        for (String role : List.of("ADMIN", "READER")) {
            mockMvc.perform(get("/api/v1/verifications")
                            .param("serviceId", serviceId.toString())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(runId.toString()))
                    .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                    .andExpect(jsonPath("$.items[0].progress.total").value(3))
                    .andExpect(jsonPath("$.items[0].progress.processed").value(2))
                    .andExpect(jsonPath("$.items[0].failureMessage").value("Safe failure"))
                    .andExpect(jsonPath("$.items[0].ticketSelections[0].ticketIds[1]").value("205"))
                    .andExpect(jsonPath("$.page.page").value(0))
                    .andExpect(jsonPath("$.page.size").value(20))
                    .andExpect(jsonPath("$.page.totalElements").value(1))
                    .andExpect(jsonPath("$.page.totalPages").value(1));
        }

        mockMvc.perform(get("/api/v1/verifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readerCanListUnfilteredEmptyHistoryWithDefaultPagination() throws Exception {
        when(queries.list(null, 0, 20)).thenReturn(new Page<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/verifications")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0));
    }

    @Test
    void malformedServiceIdIsRejectedByHistoryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/verifications")
                        .param("serviceId", "not-a-uuid")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRunReturnsNotFound() throws Exception {
        UUID runId = UUID.randomUUID();
        when(queries.get(runId)).thenThrow(VerificationResultQueryFailure.notFound(runId));
        mockMvc.perform(get("/api/v1/verifications/{runId}", runId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }
}
