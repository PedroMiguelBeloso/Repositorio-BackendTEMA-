package com.temaconsulting.agente_iso.integration.api;

import com.temaconsulting.agente_iso.integration.application.DriveFolderCatalog;
import com.temaconsulting.agente_iso.integration.domain.ExternalFolder;
import com.temaconsulting.agente_iso.shared.api.PageMapper;
import com.temaconsulting.agente_iso.shared.application.Page;
import com.temaconsulting.agente_iso.shared.dto.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/drive")
public class DriveFolderController {
    private final DriveFolderCatalog folders;

    public DriveFolderController(DriveFolderCatalog folders) {
        this.folders = folders;
    }

    @GetMapping("/folders")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ExternalFolder> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ExternalFolder> result = folders.search(query, page, size);
        return new PageResponse<>(result.items(),
                PageMapper.toPageInfo(result.page(), result.size(), result.totalElements()));
    }
}
