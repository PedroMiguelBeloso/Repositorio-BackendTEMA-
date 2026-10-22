package com.temaconsulting.agente_iso.shared.application.error;

import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;

import com.temaconsulting.agente_iso.shared.application.model.FieldProblem;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ApiException extends RuntimeException {
    private final ProblemCode problemCode;
    private final HttpStatus httpStatus;
    private final String safeMessage;
    private final List<FieldProblem> details;

    public ApiException(
            ProblemCode problemCode,
            HttpStatus httpStatus,
            String message,
            List<FieldProblem> details
    ) {
        super(message);
        this.problemCode = problemCode;
        this.httpStatus = httpStatus;
        this.safeMessage = message;
        this.details = details == null
                ? List.of()
                : List.copyOf(details);
    }

    public ProblemCode code() {
        return problemCode;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public List<FieldProblem> details() {
        return details;
    }

    public String safeMessage() {
        return safeMessage;
    }


}
