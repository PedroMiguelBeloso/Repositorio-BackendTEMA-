package com.temaconsulting.agente_iso.integration.api;

import com.temaconsulting.agente_iso.integration.application.IntegrationHealthService;
import com.temaconsulting.agente_iso.integration.domain.IntegrationStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {
    private final IntegrationHealthService health;

    public IntegrationController(IntegrationHealthService health) {
        this.health = health;
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public List<IntegrationStatus> status() {
        return health.knownStatuses();
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public List<IntegrationStatus> test() {
        return health.testAll();
    }
}
