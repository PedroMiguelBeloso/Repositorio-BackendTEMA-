package com.temaconsulting.agente_iso.shared.api.error;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.temaconsulting.agente_iso.shared.application.error.ApiException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final ProblemDetailsFactory problemDetailsFactory;

    public ApiExceptionHandler(ProblemDetailsFactory problemDetailsFactory) {
        this.problemDetailsFactory = problemDetailsFactory;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        return ResponseEntity
                .status(ex.httpStatus())
                .body(problemDetailsFactory.forApiException(ex));
    }

}