package com.temaconsulting.agente_iso.identity.infrastructure;

import com.temaconsulting.agente_iso.identity.domain.Role;
import com.temaconsulting.agente_iso.identity.domain.User;
import com.temaconsulting.agente_iso.identity.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "tema.security.bootstrap-admin.enabled",
        havingValue = "true"
)
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String email;
    private final String password;
    private final String displayName;

    public BootstrapAdminRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${tema.security.bootstrap-admin.email:}") String email,
            @Value("${tema.security.bootstrap-admin.password:}") String password,
            @Value("${tema.security.bootstrap-admin.display-name:Administrador}") String displayName
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsAdmin()) {
            return;
        }

        validateInput();

        if (userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).isPresent()) {
            return;
        }

        User admin = new User(
                UUID.randomUUID(),
                email.trim().toLowerCase(Locale.ROOT),
                displayName.trim(),
                passwordEncoder.encode(password),
                Role.ADMIN,
                true,
                clock.instant()
        );

        userRepository.insert(admin);
    }


    private void validateInput() {
        if (!StringUtils.hasText(email)) {
            throw new IllegalStateException("TEMA_BOOTSTRAP_ADMIN_EMAIL es obligatorio cuando el bootstrap está habilitado");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("TEMA_BOOTSTRAP_ADMIN_PASSWORD es obligatorio cuando el bootstrap está habilitado");
        }
        if (password.length() < 12) {
            throw new IllegalStateException("La contraseña inicial debe tener al menos 12 caracteres");
        }
        if (!StringUtils.hasText(displayName)) {
            throw new IllegalStateException("El display name inicial no puede estar vacío");
        }
    }
}
