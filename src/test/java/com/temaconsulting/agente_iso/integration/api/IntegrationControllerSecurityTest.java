package com.temaconsulting.agente_iso.integration.api;

import com.temaconsulting.agente_iso.identity.infrastructure.JsonAccessDeniedHandler;
import com.temaconsulting.agente_iso.identity.infrastructure.JsonAuthenticationEntryPoint;
import com.temaconsulting.agente_iso.identity.infrastructure.JwtRoleConverter;
import com.temaconsulting.agente_iso.identity.infrastructure.SecurityConfig;
import com.temaconsulting.agente_iso.integration.application.IntegrationHealthService;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.integration.domain.IntegrationStatus;
import com.temaconsulting.agente_iso.integration.domain.SourceHealth;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import com.temaconsulting.agente_iso.shared.api.ApiExceptionHandler;
import com.temaconsulting.agente_iso.shared.api.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.config.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationController.class)
@Import({
        SecurityConfig.class, JwtRoleConverter.class, JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class, ApiExceptionHandler.class,
        ProblemDetailsFactory.class, Configuration.class
})
class IntegrationControllerSecurityTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    IntegrationHealthService health;

    @Test
    void administratorCanReadBothStatusesWithoutStartingANewTest() throws Exception {
        when(health.knownStatuses()).thenReturn(statuses());

        mvc.perform(get("/api/v1/integrations/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("DRIVE"))
                .andExpect(jsonPath("$[0].status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$[0].checkedAt").value("2026-10-03T14:30:00Z"))
                .andExpect(jsonPath("$[1].source").value("REDMINE"));
        verify(health).knownStatuses();
    }

    @Test
    void administratorCanStartANewReadOnlyTest() throws Exception {
        when(health.testAll()).thenReturn(statuses());

        mvc.perform(post("/api/v1/integrations/test")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("DRIVE"))
                .andExpect(jsonPath("$[1].status").value("AVAILABLE"));
        verify(health).testAll();
    }

    @Test
    void readerCannotUseEitherEndpoint() throws Exception {
        mvc.perform(get("/api/v1/integrations/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/integrations/test")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotUseEitherEndpoint() throws Exception {
        mvc.perform(get("/api/v1/integrations/status"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/integrations/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unexpectedBackendFailureUsesSafeProblemDetail() throws Exception {
        when(health.testAll()).thenThrow(new ApiException(ProblemCode.INTERNAL_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno al comprobar las integraciones",
                List.of()));

        mvc.perform(post("/api/v1/integrations/test")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error interno al comprobar las integraciones"));
    }

    private List<IntegrationStatus> statuses() {
        Instant checkedAt = Instant.parse("2026-10-03T14:30:00Z");
        return List.of(
                new IntegrationStatus(IntegrationSource.DRIVE, SourceHealth.NOT_CONFIGURED,
                        checkedAt, "La conexión de Google Drive no está configurada"),
                new IntegrationStatus(IntegrationSource.REDMINE, SourceHealth.AVAILABLE,
                        checkedAt, "Consulta de lectura de Redmine realizada correctamente")
        );
    }
}
