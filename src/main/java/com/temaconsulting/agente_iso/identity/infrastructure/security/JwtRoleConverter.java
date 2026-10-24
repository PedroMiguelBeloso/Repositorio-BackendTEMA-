package com.temaconsulting.agente_iso.identity.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.List;

@Component
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object rawRole = jwt.getClaims().get("role");

        if (!(rawRole instanceof String role)) {
            return List.of();
        }

        return switch (role.toUpperCase(Locale.ROOT)) {
            case "ADMIN" -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
            case "READER" -> List.of(new SimpleGrantedAuthority("ROLE_READER"));
            default -> List.of();
        };
    }
}
