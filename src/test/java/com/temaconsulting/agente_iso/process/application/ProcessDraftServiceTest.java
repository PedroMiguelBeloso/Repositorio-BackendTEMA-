package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.api.dto.ActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.CreateVersionRequest;
import com.temaconsulting.agente_iso.process.api.dto.MacroActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.ProcessPolicyInput;
import com.temaconsulting.agente_iso.process.api.dto.SaveDraftRequest;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.DomainValidationException;
import com.temaconsulting.agente_iso.process.domain.DraftNotEditableException;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessPolicy;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.process.domain.RevisionMismatchException;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessDraftServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T15:00:00Z");

    @Mock
    private ProcessRepository processes;

    @Mock
    private ProcessVersionRepository versions;

    private ProcessDraftService service;

    @BeforeEach
    void setUp() {
        service = new ProcessDraftService(
                processes,
                versions,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsEmptyDraftWithRevisionZero() {
        UUID processId = UUID.randomUUID();
        ProcessDefinition process = process(processId);

        when(processes.findById(processId)).thenReturn(Optional.of(process));
        when(versions.findByOfficialVersion(processId, "2.0")).thenReturn(Optional.empty());
        when(versions.insertDraft(any(ProcessVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessVersion created = service.createDraft(
                processId,
                new CreateVersionRequest("2.0", LocalDate.of(2026, 9, 26), null)
        );

        assertThat(created.processId()).isEqualTo(processId);
        assertThat(created.officialVersion()).isEqualTo("2.0");
        assertThat(created.versionDate()).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(created.status()).isEqualTo(ProcessStatus.DRAFT);
        assertThat(created.revision()).isZero();
        assertThat(created.objective()).isEmpty();
        assertThat(created.owner()).isEmpty();
        assertThat(created.policies()).isEmpty();
        assertThat(created.macroActivities()).isEmpty();
        assertThat(created.publishedAt()).isNull();
        assertThat(created.createdAt()).isEqualTo(NOW);
    }

    @Test
    void savesStructurePreservingExistingIdsAndAssigningNewOnes() {
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID macroId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        ProcessVersion current = new ProcessVersion(
                versionId,
                processId,
                "1.0",
                LocalDate.of(2026, 9, 20),
                ProcessStatus.DRAFT,
                2,
                "Old objective",
                "Old owner",
                List.of(new ProcessPolicy(policyId, 0, "Old policy")),
                List.of(new MacroActivity(
                        macroId,
                        versionId,
                        "Old macro",
                        null,
                        0,
                        List.of(new Activity(
                                activityId,
                                macroId,
                                "Old activity",
                                null,
                                "Old instructions",
                                null,
                                0,
                                List.of()
                        )),
                        List.of(),
                        List.of()
                )),
                null,
                NOW
        );

        when(processes.findById(processId)).thenReturn(Optional.of(process(processId)));
        when(versions.findWithTree(versionId)).thenReturn(Optional.of(current));
        when(versions.findByOfficialVersion(processId, "1.1")).thenReturn(Optional.empty());
        when(versions.replaceDraft(any(ProcessVersion.class), eq(2)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SaveDraftRequest request = new SaveDraftRequest(
                2,
                "1.1",
                LocalDate.of(2026, 9, 27),
                "New objective",
                "New owner",
                List.of(
                        new ProcessPolicyInput(null, 0, "New policy"),
                        new ProcessPolicyInput(policyId, 1, "Updated policy")
                ),
                List.of(
                        macro(
                                null,
                                "New macro",
                                0,
                                List.of(activity(null, "New activity", 0))
                        ),
                        macro(
                                macroId,
                                "Updated macro",
                                1,
                                List.of(activity(activityId, "Updated activity", 0))
                        )
                )
        );

        ProcessVersion saved = service.saveDraft(processId, versionId, request);

        assertThat(saved.revision()).isEqualTo(3);
        assertThat(saved.policies()).hasSize(2);
        assertThat(saved.policies().get(1).id()).isEqualTo(policyId);
        assertThat(saved.macroActivities()).hasSize(2);
        assertThat(saved.macroActivities().get(1).id()).isEqualTo(macroId);
        assertThat(saved.macroActivities().get(1).activities().getFirst().id())
                .isEqualTo(activityId);
        assertThat(saved.macroActivities().getFirst().id()).isNotNull();
        assertThat(saved.macroActivities().getFirst().activities().getFirst().id()).isNotNull();
    }

    @Test
    void rejectsIdFromAnotherDraft() {
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ProcessVersion current = ProcessVersion.openDraft(
                versionId,
                processId,
                "1.0",
                LocalDate.of(2026, 9, 26),
                NOW
        );

        when(processes.findById(processId)).thenReturn(Optional.of(process(processId)));
        when(versions.findWithTree(versionId)).thenReturn(Optional.of(current));

        SaveDraftRequest request = new SaveDraftRequest(
                0,
                "1.0",
                LocalDate.of(2026, 9, 26),
                "Objective",
                "Owner",
                List.of(new ProcessPolicyInput(UUID.randomUUID(), 0, "Policy")),
                List.of()
        );

        assertThatThrownBy(() -> service.saveDraft(processId, versionId, request))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.code()).isEqualTo(ProblemCode.VALIDATION_ERROR);
                });
    }

    @Test
    void rejectsStaleRevision() {
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ProcessVersion current = new ProcessVersion(
                versionId,
                processId,
                "1.0",
                LocalDate.of(2026, 9, 26),
                ProcessStatus.DRAFT,
                3,
                "Objective",
                "Owner",
                List.of(),
                List.of(),
                null,
                NOW
        );

        when(processes.findById(processId)).thenReturn(Optional.of(process(processId)));
        when(versions.findWithTree(versionId)).thenReturn(Optional.of(current));
        lenient().when(versions.findByOfficialVersion(processId, "1.0")).thenReturn(Optional.of(current));

        SaveDraftRequest request = new SaveDraftRequest(
                2,
                "1.0",
                LocalDate.of(2026, 9, 26),
                "Objective",
                "Owner",
                List.of(),
                List.of()
        );

        assertThatThrownBy(() -> service.saveDraft(processId, versionId, request))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.code()).isEqualTo(ProblemCode.STALE_REVISION);
                });
    }

    @Test
    void rejectsDraftForUnknownProcess() {
        UUID processId = UUID.randomUUID();
        when(processes.findById(processId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraft(
                processId,
                new CreateVersionRequest("1.0", LocalDate.of(2026, 9, 26), null)
        ))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.code()).isEqualTo(ProblemCode.NOT_FOUND);
                });
    }

    @Test
    void rejectsDuplicateOfficialVersionWithinSameProcess() {
        UUID processId = UUID.randomUUID();
        ProcessVersion existing = ProcessVersion.openDraft(
                UUID.randomUUID(),
                processId,
                "1.0",
                LocalDate.of(2026, 9, 1),
                NOW
        );

        when(processes.findById(processId)).thenReturn(Optional.of(process(processId)));
        when(versions.findByOfficialVersion(processId, "1.0")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createDraft(
                processId,
                new CreateVersionRequest("1.0", LocalDate.of(2026, 9, 26), null)
        ))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.code()).isEqualTo(ProblemCode.CONFLICT);
                });
    }

    private static ProcessDefinition process(UUID processId) {
        return new ProcessDefinition(
                processId,
                ProcessCode.START_UP,
                "Start Up",
                true,
                NOW
        );
    }

    private static MacroActivityInput macro(
            UUID id,
            String name,
            int order,
            List<ActivityInput> activities
    ) {
        return new MacroActivityInput(
                id,
                name,
                null,
                order,
                List.of(),
                activities,
                List.of()
        );
    }

    private static ActivityInput activity(UUID id, String name, int order) {
        return new ActivityInput(
                id,
                name,
                null,
                "Instructions for " + name,
                null,
                order,
                List.of()
        );
    }
}