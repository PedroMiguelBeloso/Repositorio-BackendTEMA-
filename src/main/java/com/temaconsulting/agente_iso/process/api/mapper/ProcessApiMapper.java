package com.temaconsulting.agente_iso.process.api.mapper;

import com.temaconsulting.agente_iso.process.api.dto.ActivityResponse;
import com.temaconsulting.agente_iso.process.api.dto.MacroActivityResponse;
import com.temaconsulting.agente_iso.process.api.dto.EvidenceExpectationResponse;
import com.temaconsulting.agente_iso.process.api.dto.ProcessDefinitionResponse;
import com.temaconsulting.agente_iso.process.api.dto.ProcessPolicyResponse;
import com.temaconsulting.agente_iso.process.api.dto.ProcessVersionResponse;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessPolicy;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;


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
                macroActivity.inputs().stream()
                        .map(ProcessApiMapper::toResponse)
                        .toList(),
                macroActivity.activities().stream()
                        .map(ProcessApiMapper::toResponse)
                        .toList(),
                macroActivity.outputs().stream()
                        .map(ProcessApiMapper::toResponse)
                        .toList()
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
                activity.records().stream()
                        .map(ProcessApiMapper::toResponse)
                        .toList()
        );
    }

    private static EvidenceExpectationResponse toResponse(EvidenceExpectation expectation) {
        return new EvidenceExpectationResponse(
                expectation.id(),
                expectation.registryDefinitionId(),
                expectation.displayName(),
                expectation.evidenceType(),
                expectation.officialNomenclature(),
                expectation.applicability(),
                expectation.cardinality(),
                expectation.location(),
                expectation.order()
        );
    }
}
