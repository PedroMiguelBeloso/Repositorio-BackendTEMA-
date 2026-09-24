package com.temaconsulting.agente_iso.shared.api;

import com.temaconsulting.agente_iso.shared.applicacion.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private ProblemDetailsFactory problemDetailsFactory;

    @Autowired
    public GlobalExceptionHandler(ProblemDetailsFactory problemDetailsFactory) {
        this.problemDetailsFactory = problemDetailsFactory;
    }

    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        return ResponseEntity
                .status(ex.httpStatus())
                .body(problemDetailsFactory.forApiException(ex));
    }
}
