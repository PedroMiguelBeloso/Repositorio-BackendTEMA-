package com.temaconsulting.agente_iso.identity.infrastructure.security;

import com.temaconsulting.agente_iso.identity.api.dto.TokenResponse;
import com.temaconsulting.agente_iso.identity.application.port.JwtTokenIssuer;
import com.temaconsulting.agente_iso.identity.api.dto.UserResponse;
import com.temaconsulting.agente_iso.identity.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class JwtTokenService implements JwtTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration ttl;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${tema.security.jwt.ttl}") Duration ttl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public TokenResponse issue(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.email())
                .claim("role", user.role().name())
                .build();

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims
                )
        ).getTokenValue();

        UserResponse userResponse = new UserResponse(
                user.id(),
                user.email(),
                user.displayName(),
                user.role(),
                user.active(),
                user.createdAt()
        );

        return new TokenResponse(token, "Bearer", expiresAt, userResponse);
    }
}
