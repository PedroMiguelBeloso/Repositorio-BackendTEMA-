package com.temaconsulting.agente_iso.verification.api;

import com.temaconsulting.agente_iso.shared.application.FieldProblem;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import com.temaconsulting.agente_iso.verification.application.VerificationPreviewFailure;
import com.temaconsulting.agente_iso.verification.application.VerificationStartFailure;
import com.temaconsulting.agente_iso.verification.domain.VerificationDomainValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(basePackageClasses = VerificationPreviewController.class)
public class VerificationApiExceptionHandler {

    @ExceptionHandler(VerificationDomainValidationException.class)
    public ResponseEntity<ProblemDetail> handleDomainValidation(VerificationDomainValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setProperty("code", ProblemCode.VALIDATION_ERROR.name());
        problem.setProperty("details", List.of(new FieldProblem(ex.field(), ex.getMessage())));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(VerificationPreviewFailure.class)
    public ResponseEntity<ProblemDetail> handlePreviewFailure(VerificationPreviewFailure ex) {
        HttpStatus status = switch (ex.kind()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", ex.code().name());
        if (ex.field() != null) {
            problem.setProperty("details", List.of(new FieldProblem(ex.field(), ex.getMessage())));
        }
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(VerificationStartFailure.class)
    public ResponseEntity<ProblemDetail> handleStartFailure(VerificationStartFailure ex) {
        HttpStatus status = switch (ex.kind()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", ex.code().name());
        if (ex.field() != null) {
            problem.setProperty("details", List.of(new FieldProblem(ex.field(), ex.getMessage())));
        }
        return ResponseEntity.status(status).body(problem);
    }
}
