package com.temaconsulting.agente_iso.identity.infrastructure;

import com.temaconsulting.agente_iso.identity.domain.Role;
import com.temaconsulting.agente_iso.identity.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "tema.security.bootstrap-admin.enabled=false")
@Testcontainers
class JdbcUserRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcUserRepository users;

    @Test
    void detectsAdminAfterInsertingUserWithPostgresEnumRole() {
        assertThat(users.existsAdmin()).isFalse();

        User admin = User.createAdmin(
                UUID.randomUUID(),
                "admin@tema.test",
                "Administrador",
                "bcrypt-hash-for-test",
                Instant.parse("2026-09-30T12:00:00Z")
        );
        users.insert(admin);

        assertThat(users.existsAdmin()).isTrue();
        assertThat(users.findByEmail("admin@tema.test"))
                .get()
                .extracting(User::role)
                .isEqualTo(Role.ADMIN);
    }
}
