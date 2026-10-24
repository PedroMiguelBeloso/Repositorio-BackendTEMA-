package com.temaconsulting.agente_iso.integration.api;

import com.temaconsulting.agente_iso.identity.infrastructure.security.JsonAccessDeniedHandler;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JsonAuthenticationEntryPoint;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JwtRoleConverter;
import com.temaconsulting.agente_iso.identity.infrastructure.security.SecurityConfig;
import com.temaconsulting.agente_iso.integration.application.port.DriveFolderCatalog;
import com.temaconsulting.agente_iso.integration.domain.ExternalFolder;
import com.temaconsulting.agente_iso.shared.api.error.ApiExceptionHandler;
import com.temaconsulting.agente_iso.shared.api.error.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.shared.config.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriveFolderController.class)
@Import({
        SecurityConfig.class, JwtRoleConverter.class, JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class, ApiExceptionHandler.class,
        ProblemDetailsFactory.class, Configuration.class
})
class DriveFolderControllerSecurityTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    DriveFolderCatalog folders;

    @Test
    void administratorGetsFoldersInTheOpenApiPageShape() throws Exception {
        when(folders.search("Cliente", 0, 20)).thenReturn(new Page<>(
                List.of(new ExternalFolder("folder-1", "Cliente Uno", "/Servicios/Cliente Uno",
                        "https://drive.google.com/drive/folders/folder-1", "parent-1")),
                0, 20, 1));

        mvc.perform(get("/api/v1/integrations/drive/folders")
                        .param("query", "Cliente").with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("folder-1"))
                .andExpect(jsonPath("$.items[0].name").value("Cliente Uno"))
                .andExpect(jsonPath("$.items[0].path").value("/Servicios/Cliente Uno"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
        verify(folders).search("Cliente", 0, 20);
    }

    @Test
    void readerAndAnonymousCannotListDriveFolders() throws Exception {
        mvc.perform(get("/api/v1/integrations/drive/folders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/integrations/drive/folders"))
                .andExpect(status().isUnauthorized());
    }
}
