package com.temaconsulting.agente_iso.verification.infrastructure.config;

import com.temaconsulting.agente_iso.verification.domain.TicketSelectionPlanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationDomainConfiguration {
    @Bean
    TicketSelectionPlanner ticketSelectionPlanner() {
        return new TicketSelectionPlanner();
    }
}
