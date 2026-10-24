package com.temaconsulting.agente_iso.service.application;

import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.model.FieldProblem;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import org.springframework.http.HttpStatus;

import java.util.List;

final class ServiceErrors {
    private ServiceErrors() {
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(ProblemCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                "The request is invalid", List.of(new FieldProblem(field, message)));
    }

    static ApiException rule(String field, String message) {
        return new ApiException(ProblemCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                message, List.of(new FieldProblem(field, message)));
    }

    static ApiException missing(String message) {
        return new ApiException(ProblemCode.NOT_FOUND, HttpStatus.NOT_FOUND, message, List.of());
    }

    static ApiException stale() {
        return new ApiException(ProblemCode.STALE_REVISION, HttpStatus.CONFLICT,
                "Service revision has changed", List.of());
    }

    static ApiException sourceUnavailable(String message) {
        return new ApiException(ProblemCode.SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                message, List.of());
    }
}
