package com.temaconsulting.agente_iso.verification.infrastructure.lifecycle;

import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationRunStartupRecovery {
    private static final Logger log = LoggerFactory.getLogger(VerificationRunStartupRecovery.class);

    @Bean
    ApplicationRunner failInterruptedVerificationRuns(VerificationRunRepository runs) {
        return args -> {
            int interrupted = runs.failInterruptedRuns();
            if (interrupted > 0) {
                log.warn("Marked {} interrupted verification runs as FAILED", interrupted);
            }
        };
    }
}
