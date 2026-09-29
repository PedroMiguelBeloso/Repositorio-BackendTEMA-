package com.temaconsulting.agente_iso.identity.infrastructure;

import com.temaconsulting.agente_iso.identity.api.dto.TokenResponse;
import com.temaconsulting.agente_iso.identity.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final byte[] SECRET_BYTES = new byte[32];
    private static final SecretKey SECRET_KEY =
            new SecretKeySpec(SECRET_BYTES, "HmacSHA256");

    private static final Clock CLOCK =
            Clock.fixed(
                    Instant.parse("2026-09-24T20:00:00Z"),
                    java.time.ZoneOffset.UTC
            );

    @Test
    void issuedTokenContainsSubjectRoleAndExpectedExpiration() {
        JwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(SECRET_KEY)
                .algorithm(MacAlgorithm.HS256)
                .build();

        User user = User.createAdmin(
                UUID.randomUUID(),
                "admin@tema.test",
                "Admin",
                "hash",
                CLOCK.instant()
        );

        JwtTokenService service =
                new JwtTokenService(
                        encoder,
                        CLOCK,
                        Duration.ofHours(2)
                );

        TokenResponse response = service.issue(user);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(SECRET_KEY)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(CLOCK);
        decoder.setJwtValidator(timestampValidator);

        Jwt decoded = decoder.decode(response.accessToken());

        assertEquals(user.id().toString(), decoded.getSubject());
        assertEquals("ADMIN", decoded.getClaimAsString("role"));
        assertEquals(response.expiresAt(), decoded.getExpiresAt());
        assertEquals(
                Instant.parse("2026-09-24T22:00:00Z"),
                response.expiresAt()
        );
        assertEquals("Bearer", response.tokenType());
    }

    @Test
    void expiredTokenIsRejectedByDecoder() {
        Instant now = CLOCK.instant();
        JwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(SECRET_KEY)
                .algorithm(MacAlgorithm.HS256)
                .build();

        JwtClaimsSet claims = JwtClaimsSet
                .builder()
                .subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(now.minusSeconds(3600))
                .expiresAt(now.minusSeconds(61))
                .claim("role", "READER")
                .build();

        String token = encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims
                )
        ).getTokenValue();

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(SECRET_KEY)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(CLOCK);
        decoder.setJwtValidator(timestampValidator);

        assertThrows(
                org.springframework.security.oauth2.jwt.JwtValidationException.class,
                () -> decoder.decode(token)
        );
    }
}