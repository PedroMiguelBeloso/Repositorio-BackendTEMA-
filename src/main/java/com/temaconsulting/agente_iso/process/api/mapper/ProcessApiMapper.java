package com.temaconsulting.agente_iso.process.api.mapper;

import com.temaconsulting.agente_iso.process.api.dto.ActivityResponse;
import com.temaconsulting.agente_iso.process.api.dto.MacroActivityResponse;
import com.temaconsulting.agente_iso.process.api.dto.ProcessDefinitionResponse;
import com.temaconsulting.agente_iso.process.api.dto.ProcessPolicyResponse;
import com.temaconsulting.agente_iso.process.api.dto.ProcessVersionResponse;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessPolicy;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;

import java.util.List;

public final class ProcessApiMapper {

    private ProcessApiMapper() {
    }

    public static ProcessDefinitionResponse toResponse(ProcessDefinition process) {
        return new ProcessDefinitionResponse(
                process.id(),
                process.code(),
                process.name(),
                process.m2Enabled(),
                process.createdAt()
        );
    }

    public static ProcessVersionResponse toResponse(ProcessVersion version) {
        return new ProcessVersionResponse(
                version.id(),
                version.processId(),
                version.officialVersion(),
                version.versionDate(),
                version.status(),
                version.revision(),
                version.objective(),
                version.owner(),
                version.policies().stream()
                        .map(ProcessApiMapper::toResponse)
                        .toList(),
                version.macroActivities().stream()
                        .map(ProcessApiMapper::toResponse)
                        .toList(),
                version.publishedAt(),
                version.createdAt()
        );
    }

    private static ProcessPolicyResponse toResponse(ProcessPolicy policy) {
        return new ProcessPolicyResponse(
                policy.id(),
                policy.order(),
                policy.text()
        );
    }

    private static MacroActivityResponse toResponse(MacroActivity macroActivity) {
        return new MacroActivityResponse(
                macroActivity.id(),
                macroActivity.name(),
                macroActivity.description(),
                macroActivity.order(),
                // WBS 5.1.3 will populate evidence inputs/outputs.
                List.of(),
                macroActivity.activities().stream()
                        .map(ProcessApiMapper::toResponse)
                        .toList(),
                // WBS 5.1.3 will populate evidence inputs/outputs.
                List.of()
        );
    }

    private static ActivityResponse toResponse(Activity activity) {
        return new ActivityResponse(
                activity.id(),
                activity.name(),
                activity.responsible(),
                activity.instructions(),
                activity.etaText(),
                activity.order(),
                // WBS 5.1.3 will populate activity records.
                List.of()
        );
    }
}
