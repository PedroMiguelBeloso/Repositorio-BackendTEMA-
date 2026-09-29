package com.temaconsulting.agente_iso.identity.api;

import com.temaconsulting.agente_iso.identity.api.dto.CreateReaderRequest;
import com.temaconsulting.agente_iso.identity.api.dto.UserResponse;
import com.temaconsulting.agente_iso.identity.api.mapper.UserApiMapper;
import com.temaconsulting.agente_iso.identity.application.UserService;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.shared.dto.Page;
import com.temaconsulting.agente_iso.shared.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserApiMapper userApiMapper;

    public UserController(UserService userService, UserApiMapper userApiMapper) {
        this.userService = userService;
        this.userApiMapper = userApiMapper;
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Page<User> users = userService.list(page, size);
        return ResponseEntity.ok(
                new PageResponse<>(
                        users.items().stream().map(userApiMapper::toResponse).toList(),
                        users.page()
                )
        );
    }

    @PostMapping
    public ResponseEntity<UserResponse> createReader(
            @Valid @RequestBody CreateReaderRequest request
    ) {
        User reader = userService.createReader(request.email(), request.displayName(), request.initialPassword());
        return ResponseEntity.status(201).body(userApiMapper.toResponse(reader));
    }
}
