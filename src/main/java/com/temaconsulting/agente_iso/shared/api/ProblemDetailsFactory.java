package com.temaconsulting.agente_iso.shared.api;

import com.temaconsulting.agente_iso.shared.applicacion.ApiException;
import com.temaconsulting.agente_iso.shared.applicacion.ProblemCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailsFactory {

    public ProblemDetail forApiException(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                ex.httpStatus(),
                ex.safeMessage()
        );

        problem.setProperty("code", ex.code());

        if (!ex.details().isEmpty()) {
            problem.setProperty("details", ex.details());
        }

        return problem;
    }

    public ProblemDetail forSecurity(
            HttpStatus status,
            ProblemCode code,
            String detail
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                detail
        );

        problem.setProperty("code", code);

        return problem;
    }

    public ProblemDetail forUnexpected(Exception ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
    }
}