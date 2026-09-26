package com.temaconsulting.agente_iso.identity.api;

import com.temaconsulting.agente_iso.identity.api.dto.ChangePasswordRequest;
import com.temaconsulting.agente_iso.identity.api.dto.LoginRequest;
import com.temaconsulting.agente_iso.identity.api.dto.TokenResponse;
import com.temaconsulting.agente_iso.identity.api.dto.UserResponse;
import com.temaconsulting.agente_iso.identity.api.mapper.UserApiMapper;
import com.temaconsulting.agente_iso.identity.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserApiMapper userApiMapper;

    public AuthController(AuthService authService, UserApiMapper userApiMapper) {
        this.authService = authService;
        this.userApiMapper = userApiMapper;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(userApiMapper.toResponse(authService.currentUser(userId)));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
