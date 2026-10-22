package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.application.error.DuplicateProcessCodeException;
import com.temaconsulting.agente_iso.process.application.port.ProcessRepository;

import com.temaconsulting.agente_iso.process.domain.error.DomainValidationException;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.model.FieldProblem;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
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
        final ProcessDefinition process;
        try {
            process = ProcessDefinition.create(
                    UUID.randomUUID(),
                    code,
                    name,
                    clock.instant()
            );
        } catch (DomainValidationException ex) {
            throw validationError(ex.field(), ex.getMessage());
        }

        if (processes.findByCode(process.code()).isPresent()) {
            throw conflict("A process with code " + process.code().name() + " already exists");
        }

        try {
            return processes.insert(process);
        } catch (DuplicateProcessCodeException ex) {
            throw conflict("A process with code " + process.code().name() + " already exists");
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

    static ApiException staleRevision(String message) {
        return new ApiException(
                ProblemCode.STALE_REVISION,
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
