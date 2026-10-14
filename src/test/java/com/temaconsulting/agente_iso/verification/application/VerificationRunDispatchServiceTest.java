package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.shared.application.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificationRunDispatchServiceTest {
    @Test
    void rejectedJobFailsThePersistedRunAndReturnsServiceUnavailable() {
        VerificationRunWorker worker = mock(VerificationRunWorker.class);
        VerificationRunRepository runs = mock(VerificationRunRepository.class);
        UUID runId = UUID.randomUUID();
        doThrow(new TaskRejectedException("queue full")).when(worker).process(runId);

        assertThatThrownBy(() -> new VerificationRunDispatchService(worker, runs).dispatch(runId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).httpStatus())
                        .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        verify(runs).markFailed(runId, "La cola de verificaciones está llena. Intente nuevamente.");
    }
}
