package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.port.VerificationRunRepository;

import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VerificationRunDispatchService {
    private final VerificationRunWorker worker;
    private final VerificationRunRepository runs;

    public VerificationRunDispatchService(VerificationRunWorker worker, VerificationRunRepository runs) {
        this.worker = worker;
        this.runs = runs;
    }

    public void dispatch(UUID runId) {
        try {
            worker.process(runId);
        } catch (TaskRejectedException ex) {
            runs.markFailed(runId, "La cola de verificaciones está llena. Intente nuevamente.");
            throw new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "La cola de verificaciones está llena. Intente nuevamente.", List.of());
        }
    }
}
