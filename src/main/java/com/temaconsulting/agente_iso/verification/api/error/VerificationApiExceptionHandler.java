package com.temaconsulting.agente_iso.verification.api.error;

import com.temaconsulting.agente_iso.verification.api.VerificationPreviewController;

import com.temaconsulting.agente_iso.shared.application.model.FieldProblem;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import com.temaconsulting.agente_iso.verification.application.error.VerificationPreviewFailure;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultConflictException;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultDecisionFailure;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultQueryFailure;
import com.temaconsulting.agente_iso.verification.application.error.VerificationResultValidationException;
import com.temaconsulting.agente_iso.verification.application.error.VerificationStartFailure;
import com.temaconsulting.agente_iso.verification.domain.error.VerificationDomainValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

@RestControllerAdvice(basePackageClasses = VerificationPreviewController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
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

    @ExceptionHandler(VerificationResultValidationException.class)
    public ResponseEntity<ProblemDetail> handleResultValidation(VerificationResultValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("details", List.of(new FieldProblem(ex.field(), ex.getMessage())));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(VerificationResultConflictException.class)
    public ResponseEntity<ProblemDetail> handleResultConflict(VerificationResultConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", "CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(VerificationResultQueryFailure.class)
    public ResponseEntity<ProblemDetail> handleResultQueryFailure(VerificationResultQueryFailure ex) {
        HttpStatus status = ex.kind() == VerificationResultQueryFailure.Kind.NOT_FOUND
                ? HttpStatus.NOT_FOUND
                : HttpStatus.CONFLICT;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", ex.kind().name());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(VerificationResultDecisionFailure.class)
    public ResponseEntity<ProblemDetail> handleResultDecisionFailure(VerificationResultDecisionFailure ex) {
        HttpStatus status = switch (ex.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INCOMPATIBLE_STATUS, INCOMPATIBLE_APPLICABILITY -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", ex.kind().name());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleInvalidDecisionRequest(MethodArgumentNotValidException ex) {
        List<FieldProblem> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(), "is required or has an invalid value"))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body contains invalid fields"
        );
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("details", details);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableDecisionRequest(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body is invalid"
        );
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("details", List.of(new FieldProblem("request", "Request body is invalid")));
        return ResponseEntity.badRequest().body(problem);
    }
}
