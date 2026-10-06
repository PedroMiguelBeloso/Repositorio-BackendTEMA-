package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.process.application.ProcessAssignmentInfo;
import com.temaconsulting.agente_iso.process.application.ProcessAssignmentReader;
import com.temaconsulting.agente_iso.process.application.ProcessVersionNotFoundException;
import com.temaconsulting.agente_iso.process.application.ProcessVersionNotPublishedException;
import com.temaconsulting.agente_iso.process.application.PublishedProcessReader;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.service.application.ServiceSnapshotReader;
import com.temaconsulting.agente_iso.verification.domain.PreviewExpectation;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionPlanner;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionRuleViolation;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VerificationScopeResolver {
    private final ServiceSnapshotReader services;
    private final PublishedProcessReader processes;
    private final ProcessAssignmentReader processAssignments;
    private final TicketSelectionPlanner ticketSelectionPlanner;

    public VerificationScopeResolver(
            ServiceSnapshotReader services,
            PublishedProcessReader processes,
            ProcessAssignmentReader processAssignments,
            TicketSelectionPlanner ticketSelectionPlanner
    ) {
        this.services = services;
        this.processes = processes;
        this.processAssignments = processAssignments;
        this.ticketSelectionPlanner = ticketSelectionPlanner;
    }

    /**
     * Resolves the exact immutable process definition used by a preview.
     * The same resolver is intentionally reusable by the future start-run use case.
     */
    public VerificationPreview resolve(VerificationTarget target) {
        ServiceSnapshotReader.ServiceSnapshot service = services.find(target.serviceId())
                .orElseThrow(() -> VerificationErrors.missing("Service not found: " + target.serviceId()));

        ProcessVersion version = publishedVersion(target.processVersionId());

        ProcessAssignmentInfo assignment = processAssignments.find(target.processVersionId())
                .orElseThrow(() -> VerificationErrors.missing(
                        "Process definition not found for version: " + target.processVersionId()));

        validateVersionAssignment(service, version, assignment, target.serviceId());
        validateM2Process(assignment);

        List<MacroActivity> macros = selectMacroActivities(version, target);
        List<PreviewExpectation> expectations = new ArrayList<>();
        List<TicketSelectionGroup> groups = new ArrayList<>();

        for (MacroActivity macro : macros) {
            TicketSelectionGroup group = buildTicketGroup(macro);
            String groupId = group == null ? null : group.id();

            for (EvidenceExpectation expectation : macro.expectationsInVerificationOrder()) {
                String expectationGroupId = group != null
                        && group.relatedExpectationIds().contains(expectation.id())
                        ? groupId
                        : null;
                expectations.add(toPreviewExpectation(expectation, expectationGroupId));
            }
            if (group != null) {
                groups.add(group);
            }
        }

        return new VerificationPreview(
                target.serviceId(),
                service.revision(),
                target.processVersionId(),
                target.scope(),
                target.scope() == VerificationScope.MACRO_ACTIVITY ? target.macroActivityId() : null,
                List.copyOf(expectations),
                List.copyOf(groups)
        );
    }

    private ProcessVersion publishedVersion(UUID versionId) {
        try {
            return processes.getPublished(versionId);
        } catch (ProcessVersionNotFoundException ex) {
            throw VerificationErrors.missing(ex.getMessage());
        } catch (ProcessVersionNotPublishedException ex) {
            throw VerificationErrors.rule("processVersionId", ex.getMessage());
        }
    }

    private void validateVersionAssignment(
            ServiceSnapshotReader.ServiceSnapshot service,
            ProcessVersion version,
            ProcessAssignmentInfo assignment,
            UUID serviceId
    ) {
        boolean assigned = service.processAssignments().stream()
                .anyMatch(a -> a.processVersionId().equals(version.id()));
        if (!assigned) {
            throw VerificationErrors.rule(
                    "processVersionId",
                    "Process version " + version.id() + " is not assigned to service " + serviceId
            );
        }
        if (!assignment.processVersionId().equals(version.id())) {
            throw VerificationErrors.rule(
                    "processVersionId",
                    "Process assignment does not match the selected version"
            );
        }
        if (!assignment.processId().equals(version.processId())) {
            throw VerificationErrors.rule(
                    "processVersionId",
                    "Process version does not belong to the expected process"
            );
        }
    }

    private void validateM2Process(ProcessAssignmentInfo assignment) {
        if (assignment.status() != ProcessStatus.PUBLISHED
                || !assignment.m2Enabled()
                || !assignment.code().isM2Enabled()) {
            throw VerificationErrors.unsupportedScope(
                    "processVersionId",
                    "The selected process is not enabled for M2 verification"
            );
        }
    }

    private List<MacroActivity> selectMacroActivities(ProcessVersion version, VerificationTarget target) {
        if (target.scope() == VerificationScope.PROCESS) {
            return version.macroActivities();
        }

        return version.macroActivities().stream()
                .filter(macro -> macro.id().equals(target.macroActivityId()))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> VerificationErrors.rule(
                        "macroActivityId",
                        "Macro activity " + target.macroActivityId()
                                + " does not belong to process version " + version.id()
                ));
    }

    private PreviewExpectation toPreviewExpectation(EvidenceExpectation expectation, String ticketGroupId) {
        return new PreviewExpectation(
                expectation.id(),
                expectation.macroActivityId(),
                expectation.activityId(),
                expectation.location(),
                expectation.displayName(),
                expectation.evidenceType(),
                expectation.applicability(),
                expectation.cardinality(),
                ticketGroupId
        );
    }

    private TicketSelectionGroup buildTicketGroup(MacroActivity macro) {
        try {
            return ticketSelectionPlanner.plan(macro).orElse(null);
        } catch (TicketSelectionRuleViolation ex) {
            throw VerificationErrors.ticketSelectionRule("macroActivityId", ex.getMessage());
        }
    }
}
