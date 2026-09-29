package com.temaconsulting.agente_iso.identity.api;

import com.temaconsulting.agente_iso.identity.application.CurrentUserNotFoundException;
import com.temaconsulting.agente_iso.identity.application.DuplicateUserEmailException;
import com.temaconsulting.agente_iso.identity.application.InvalidCredentialsException;
import com.temaconsulting.agente_iso.identity.domain.UserDomainValidationException;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = UserController.class)
public class IdentityApiExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemCode.VALIDATION_ERROR,
                ex.getMessage()
        );
    }

    @ExceptionHandler(UserDomainValidationException.class)
    public ResponseEntity<ProblemDetail> handleUserDomainValidation(UserDomainValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, ProblemCode.VALIDATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(DuplicateUserEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateUserEmail(DuplicateUserEmailException ex) {
        return problem(HttpStatus.CONFLICT, ProblemCode.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({InvalidCredentialsException.class, CurrentUserNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleAuthenticationFailure(RuntimeException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED, ex.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            ProblemCode code,
            String detail
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return ResponseEntity.status(status).body(problem);
    }
}
