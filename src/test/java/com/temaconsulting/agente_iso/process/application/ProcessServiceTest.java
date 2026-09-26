package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T15:00:00Z");

    @Mock
    private ProcessRepository repository;

    private ProcessService service;

    @BeforeEach
    void setUp() {
        service = new ProcessService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsM2EnabledProcessForSupportedM2Code() {
        when(repository.findByCode(ProcessCode.START_UP)).thenReturn(Optional.empty());
        when(repository.insert(any(ProcessDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessDefinition created = service.create(ProcessCode.START_UP, "Start Up");

        assertThat(created.code()).isEqualTo(ProcessCode.START_UP);
        assertThat(created.name()).isEqualTo("Start Up");
        assertThat(created.m2Enabled()).isTrue();
        assertThat(created.createdAt()).isEqualTo(NOW);

        ArgumentCaptor<ProcessDefinition> captor = ArgumentCaptor.forClass(ProcessDefinition.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().m2Enabled()).isTrue();
    }

    @Test
    void createsQualityProcessDisabledForM2() {
        when(repository.findByCode(ProcessCode.QUALITY_CONTINUOUS_IMPROVEMENT))
                .thenReturn(Optional.empty());
        when(repository.insert(any(ProcessDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessDefinition created = service.create(
                ProcessCode.QUALITY_CONTINUOUS_IMPROVEMENT,
                "Quality and Continuous Improvement"
        );

        assertThat(created.m2Enabled()).isFalse();
    }

    @Test
    void rejectsDuplicateProcessCode() {
        ProcessDefinition existing = new ProcessDefinition(
                UUID.randomUUID(),
                ProcessCode.TAKE_OVER,
                "Take Over",
                true,
                NOW
        );
        when(repository.findByCode(ProcessCode.TAKE_OVER)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(ProcessCode.TAKE_OVER, "Take Over"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.code()).isEqualTo(ProblemCode.CONFLICT);
                });
    }

    @Test
    void returnsNotFoundForUnknownProcessId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.code()).isEqualTo(ProblemCode.NOT_FOUND);
                });
    }
}
