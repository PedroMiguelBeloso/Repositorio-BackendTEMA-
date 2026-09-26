package com.temaconsulting.agente_iso.identity.api.mapper;

import com.temaconsulting.agente_iso.identity.api.dto.UserResponse;
import com.temaconsulting.agente_iso.identity.domain.User;
import org.springframework.stereotype.Component;

@Component
public class UserApiMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.id(),
                user.email(),
                user.displayName(),
                user.role(),
                user.active(),
                user.createdAt()
        );
    }
}
