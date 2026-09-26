package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.shared.application.ApiException;
import com.temaconsulting.agente_iso.shared.application.FieldProblem;
import com.temaconsulting.agente_iso.shared.application.Page;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessService {

    private final ProcessRepository processes;
    private final Clock clock;

    public ProcessService(ProcessRepository processes, Clock clock) {
        this.processes = processes;
        this.clock = clock;
    }

    public Page<ProcessDefinition> list(int page, int size) {
        validatePagination(page, size);
        return processes.list(page, size);
    }

    public ProcessDefinition create(ProcessCode code, String name) {
        if (code == null) {
            throw validationError("code", "Process code is required");
        }

        String normalizedName = requireText(name, "name", "Process name is required");

        if (processes.findByCode(code).isPresent()) {
            throw conflict("A process with code " + code.name() + " already exists");
        }

        ProcessDefinition process = new ProcessDefinition(
                UUID.randomUUID(),
                code,
                normalizedName,
                code.isM2Enabled(),
                clock.instant()
        );

        try {
            return processes.insert(process);
        } catch (DuplicateProcessCodeException ex) {
            throw conflict("A process with code " + code.name() + " already exists");
        }
    }

    public ProcessDefinition get(UUID id) {
        return processes.findById(id)
                .orElseThrow(() -> notFound("Process not found: " + id));
    }

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw validationError("page", "Page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw validationError("size", "Size must be between 1 and 100");
        }
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw validationError(field, message);
        }
        return value.trim();
    }

    static ApiException notFound(String message) {
        return new ApiException(
                ProblemCode.NOT_FOUND,
                HttpStatus.NOT_FOUND,
                message,
                List.of()
        );
    }

    static ApiException conflict(String message) {
        return new ApiException(
                ProblemCode.CONFLICT,
                HttpStatus.CONFLICT,
                message,
                List.of()
        );
    }

    static ApiException validationError(String field, String message) {
        return new ApiException(
                ProblemCode.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST,
                "The request is invalid",
                List.of(new FieldProblem(field, message))
        );
    }
}
