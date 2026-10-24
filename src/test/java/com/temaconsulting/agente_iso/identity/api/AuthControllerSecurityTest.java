package com.temaconsulting.agente_iso.identity.api;

import com.temaconsulting.agente_iso.identity.api.error.IdentityApiExceptionHandler;

import com.temaconsulting.agente_iso.identity.api.dto.TokenResponse;
import com.temaconsulting.agente_iso.identity.api.dto.UserResponse;
import com.temaconsulting.agente_iso.identity.api.mapper.UserApiMapper;
import com.temaconsulting.agente_iso.identity.application.AuthService;
import com.temaconsulting.agente_iso.identity.application.error.DuplicateUserEmailException;
import com.temaconsulting.agente_iso.identity.application.error.InvalidCredentialsException;
import com.temaconsulting.agente_iso.identity.application.UserService;
import com.temaconsulting.agente_iso.identity.domain.Role;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JsonAccessDeniedHandler;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JsonAuthenticationEntryPoint;
import com.temaconsulting.agente_iso.identity.infrastructure.security.JwtRoleConverter;
import com.temaconsulting.agente_iso.identity.infrastructure.security.SecurityConfig;
import com.temaconsulting.agente_iso.shared.api.error.ApiExceptionHandler;
import com.temaconsulting.agente_iso.shared.api.error.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.config.Configuration;
import com.temaconsulting.agente_iso.shared.api.dto.Page;
import com.temaconsulting.agente_iso.shared.api.dto.PageInfo;
import com.temaconsulting.agente_iso.shared.api.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({
        SecurityConfig.class,
        JwtRoleConverter.class,
        JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class,
        UserApiMapper.class,
        ApiExceptionHandler.class,
        IdentityApiExceptionHandler.class,
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
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"admin@tema.test\",\"password\":\"secret\"}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_READER")
                                ))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessUsersEndpoint() throws Exception {
        User user = User.createAdmin(
                USER_ID,
                "admin@tema.test",
                "Admin",
                "hash",
                Instant.parse("2026-09-24T20:00:00Z")
        );

        when(userService.list(0, 20)).thenReturn(
                new Page<>(
                        List.of(user),
                        new PageInfo(0, 20, 1, 1)
                )
        );

        mockMvc.perform(
                        get("/api/v1/users")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items[0].password_hash").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
    }

    @Test
    void meUsesAuthenticatedSubject() throws Exception {
        User expected = User.createReader(
                USER_ID,
                "reader@tema.test",
                "Reader",
                "hash",
                Instant.parse("2026-09-24T20:00:00Z")
        );
        when(authService.currentUser(USER_ID)).thenReturn(expected);

        mockMvc.perform(
                        get("/api/v1/auth/me")
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
                        put("/api/v1/auth/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}")
                )
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(authService).changePassword(
                USER_ID,
                "old-password",
                "new-password"
        );
    }

    @Test
    void readerCanChangeOwnPassword() throws Exception {
        mockMvc.perform(
                        put("/api/v1/auth/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))
                                        .authorities(new SimpleGrantedAuthority("ROLE_READER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}")
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void readerCannotCreateReader() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_READER")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"reader2@tema.test\",\"displayName\":\"Reader 2\",\"initialPassword\":\"initial-password\"}")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidReaderCreationReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"not-an-email\",\"displayName\":\"\",\"initialPassword\":\"short\"}")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPaginationReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users?page=-1&size=101")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                ))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateReaderEmailReturnsConflict() throws Exception {
        when(userService.createReader(
                "reader@tema.test",
                "Reader",
                "initial-password"
        )).thenThrow(new DuplicateUserEmailException());

        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"reader@tema.test\",\"displayName\":\"Reader\",\"initialPassword\":\"initial-password\"}")
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void wrongCurrentPasswordReturnsUnauthorized() throws Exception {
        doThrow(new InvalidCredentialsException()).when(authService).changePassword(
                USER_ID,
                "wrong-password",
                "new-password"
        );

        mockMvc.perform(
                        put("/api/v1/auth/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"new-password\"}")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void adminCanCreateReader() throws Exception {
        User reader = User.createReader(
                USER_ID,
                "reader@tema.test",
                "Reader",
                "hash",
                Instant.parse("2026-09-24T20:00:00Z")
        );
        when(userService.createReader(
                "reader@tema.test",
                "Reader",
                "initial-password"
        )).thenReturn(reader);

        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"reader@tema.test\",\"displayName\":\"Reader\",\"initialPassword\":\"initial-password\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void readerCannotCreateProcessAtVersionedRoute() throws Exception {
        mockMvc.perform(post("/api/v1/processes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void readerCannotCreateRegistryDefinitionAtVersionedRoute() throws Exception {
        mockMvc.perform(post("/api/v1/registry-definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void readerCannotPublishProcessVersionAtVersionedRoute() throws Exception {
        mockMvc.perform(post("/api/v1/processes/process-id/versions/version-id/publish")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadVersionedProcessRoute() throws Exception {
        mockMvc.perform(get("/api/v1/processes"))
                .andExpect(status().isUnauthorized());
    }
}
