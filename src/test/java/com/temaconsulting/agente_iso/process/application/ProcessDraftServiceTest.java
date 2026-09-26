package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.api.dto.CreateVersionRequest;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        ProcessDefinition process = new ProcessDefinition(
                processId,
                ProcessCode.START_UP,
                "Start Up",
                true,
                NOW
        );

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
        assertThat(created.publishedAt()).isNull();
        assertThat(created.createdAt()).isEqualTo(NOW);
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
        ProcessDefinition process = new ProcessDefinition(
                processId,
                ProcessCode.START_UP,
                "Start Up",
                true,
                NOW
        );
        ProcessVersion existing = new ProcessVersion(
                UUID.randomUUID(),
                processId,
                "1.0",
                LocalDate.of(2026, 9, 1),
                ProcessStatus.DRAFT,
                0,
                "",
                "",
                null,
                NOW
        );

        when(processes.findById(processId)).thenReturn(Optional.of(process));
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
}
