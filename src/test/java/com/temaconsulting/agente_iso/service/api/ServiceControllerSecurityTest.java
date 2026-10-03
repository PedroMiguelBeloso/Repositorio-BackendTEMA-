package com.temaconsulting.agente_iso.service.api;

import com.temaconsulting.agente_iso.identity.infrastructure.JsonAccessDeniedHandler;
import com.temaconsulting.agente_iso.identity.infrastructure.JsonAuthenticationEntryPoint;
import com.temaconsulting.agente_iso.identity.infrastructure.JwtRoleConverter;
import com.temaconsulting.agente_iso.identity.infrastructure.SecurityConfig;
import com.temaconsulting.agente_iso.service.application.ServiceConfigurationService;
import com.temaconsulting.agente_iso.service.application.ServiceQueryService;
import com.temaconsulting.agente_iso.shared.api.ApiExceptionHandler;
import com.temaconsulting.agente_iso.shared.api.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.application.Page;
import com.temaconsulting.agente_iso.shared.config.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceController.class)
@Import({
        SecurityConfig.class, JwtRoleConverter.class, JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class, ApiExceptionHandler.class,
        ProblemDetailsFactory.class, Configuration.class
})
class ServiceControllerSecurityTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServiceConfigurationService configuration;

    @MockitoBean
    ServiceQueryService queries;

    @Test
    void servicesRouteIsPresentAndReaderCanList() throws Exception {
        when(queries.list(0, 20)).thenReturn(new Page<>(List.of(), 0, 20, 0));
        mvc.perform(get("/api/v1/services?page=0&size=20")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void anonymousCannotListServices() throws Exception {
        mvc.perform(get("/api/v1/services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readerCannotCreateService() throws Exception {
        mvc.perform(post("/api/v1/services")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
