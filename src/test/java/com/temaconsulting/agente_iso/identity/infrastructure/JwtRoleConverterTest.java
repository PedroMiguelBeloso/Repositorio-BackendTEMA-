package com.temaconsulting.agente_iso.identity.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    @Test
    void adminBecomesRoleAdmin() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRole("ADMIN"));
        assertEquals("ROLE_ADMIN", authorities.iterator().next().getAuthority());
    }

    @Test
    void readerBecomesRoleReader() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRole("READER"));
        assertEquals("ROLE_READER", authorities.iterator().next().getAuthority());
    }

    @Test
    void unknownRoleProducesNoAuthority() {
        assertEquals(0, converter.convert(jwtWithRole("UNKNOWN")).size());
    }

    private Jwt jwtWithRole(String role) {
        return new Jwt(
                "token",
                Instant.parse("2026-09-24T20:00:00Z"),
                Instant.parse("2026-09-24T22:00:00Z"),
                Map.of("alg", "HS256"),
                Map.of("sub", "00000000-0000-0000-0000-000000000001", "role", role)
        );
    }
}
