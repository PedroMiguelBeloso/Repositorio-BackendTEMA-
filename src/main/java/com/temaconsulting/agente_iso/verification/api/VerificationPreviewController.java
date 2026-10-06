package com.temaconsulting.agente_iso.verification.api;

import com.temaconsulting.agente_iso.verification.api.dto.VerificationPreviewResponse;
import com.temaconsulting.agente_iso.verification.api.mapper.VerificationApiMapper;
import com.temaconsulting.agente_iso.verification.api.dto.VerificationTarget;
import com.temaconsulting.agente_iso.verification.application.VerificationPreviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/verifications/preview")
public class VerificationPreviewController {
    private final VerificationPreviewService previews;

    public VerificationPreviewController(VerificationPreviewService previews) {
        this.previews = previews;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationPreviewResponse preview(@Valid @RequestBody VerificationTarget request) {
        return VerificationApiMapper.toResponse(previews.preview(request.toDomain()));
    }
}
