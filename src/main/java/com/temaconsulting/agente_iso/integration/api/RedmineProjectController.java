package com.temaconsulting.agente_iso.integration.api;

import com.temaconsulting.agente_iso.integration.api.dto.RedmineProjectResponse;
import com.temaconsulting.agente_iso.integration.application.port.RedmineProjectCatalog;
import com.temaconsulting.agente_iso.integration.domain.ExternalProject;
import com.temaconsulting.agente_iso.shared.api.mapper.PageMapper;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.shared.api.dto.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
public class RedmineProjectController {
    private final RedmineProjectCatalog projects;

    public RedmineProjectController(RedmineProjectCatalog projects) {
        this.projects = projects;
    }

    @GetMapping("/redmine/projects")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<RedmineProjectResponse> listProjects(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ExternalProject> result = projects.searchProjects(query, page, size);
        return new PageResponse<>(
                result.items().stream().map(project -> new RedmineProjectResponse(project.id(), project.name(), project.identifier())).toList(),
                PageMapper.toPageInfo(result.page(), result.size(), result.totalElements())
        );
    }
}
