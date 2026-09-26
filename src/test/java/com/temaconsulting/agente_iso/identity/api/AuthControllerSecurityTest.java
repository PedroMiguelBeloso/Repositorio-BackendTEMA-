package com.temaconsulting.agente_iso.identity.api;

import com.temaconsulting.agente_iso.identity.api.dto.TokenResponse;
import com.temaconsulting.agente_iso.identity.api.dto.UserResponse;
import com.temaconsulting.agente_iso.identity.api.mapper.UserApiMapper;
import com.temaconsulting.agente_iso.identity.application.AuthService;
import com.temaconsulting.agente_iso.identity.application.UserService;
import com.temaconsulting.agente_iso.identity.domain.Role;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.infrastructure.JsonAccessDeniedHandler;
import com.temaconsulting.agente_iso.identity.infrastructure.JsonAuthenticationEntryPoint;
import com.temaconsulting.agente_iso.identity.infrastructure.JwtRoleConverter;
import com.temaconsulting.agente_iso.identity.infrastructure.SecurityConfig;
import com.temaconsulting.agente_iso.shared.api.ApiExceptionHandler;
import com.temaconsulting.agente_iso.shared.api.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.config.Configuration;
import com.temaconsulting.agente_iso.shared.dto.Page;
import com.temaconsulting.agente_iso.shared.dto.PageInfo;
import com.temaconsulting.agente_iso.shared.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({
        SecurityConfig.class,
        JwtRoleConverter.class,
        JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class,
        UserApiMapper.class,
        ApiExceptionHandler.class,
        ProblemDetailsFactory.class,
        Configuration.class
})
class AuthControllerSecurityTest {

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    UserService userService;

    @Test
    void loginDoesNotRequireBearerToken() throws Exception {
        TokenResponse tokenResponse = new TokenResponse(
                "jwt",
                "Bearer",
                Instant.parse("2026-09-24T22:00:00Z"),
                new UserResponse(
                        USER_ID,
                        "admin@tema.test",
                        "Admin",
                        Role.ADMIN,
                        true,
                        Instant.parse("2026-09-24T20:00:00Z")
                )
        );
        when(authService.login(any())).thenReturn(tokenResponse);

        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"admin@tema.test\",\"password\":\"secret\"}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(
                        get("/users")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_READER")
                                ))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessUsersEndpoint() throws Exception {
        User user = new User(
                USER_ID,
                "admin@tema.test",
                "Admin",
                "hash",
                Role.ADMIN,
                true,
                Instant.parse("2026-09-24T20:00:00Z")
        );

        when(userService.list(0, 20)).thenReturn(
                new Page<>(
                        List.of(user),
                        new PageInfo(0, 20, 1, 1)
                )
        );

        mockMvc.perform(
                        get("/users")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void meUsesAuthenticatedSubject() throws Exception {
        User expected = new User(
                USER_ID,
                "reader@tema.test",
                "Reader",
                "hash",
                Role.READER,
                true,
                Instant.parse("2026-09-24T20:00:00Z")
        );
        when(authService.currentUser(USER_ID)).thenReturn(expected);

        mockMvc.perform(
                        get("/auth/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("reader@tema.test")
                ));
    }

    @Test
    void passwordChangeUsesAuthenticatedSubject() throws Exception {
        mockMvc.perform(
                        put("/auth/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}")
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCanCreateReader() throws Exception {
        User reader = new User(
                USER_ID,
                "reader@tema.test",
                "Reader",
                "hash",
                Role.READER,
                true,
                Instant.parse("2026-09-24T20:00:00Z")
        );
        when(userService.createReader(
                "reader@tema.test",
                "Reader",
                "initial-password"
        )).thenReturn(reader);

        mockMvc.perform(
                        post("/users")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"reader@tema.test\",\"displayName\":\"Reader\",\"initialPassword\":\"initial-password\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

}
