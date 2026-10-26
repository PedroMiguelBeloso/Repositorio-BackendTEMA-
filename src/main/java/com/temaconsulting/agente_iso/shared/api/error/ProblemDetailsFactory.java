package com.temaconsulting.agente_iso.shared.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import com.temaconsulting.agente_iso.shared.api.RequestCorrelationFilter;

import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;

@Component
public class ProblemDetailsFactory {

    public ProblemDetail forApiException(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                ex.httpStatus(),
                ex.safeMessage()
        );
        problem.setProperty("code", ex.code().name());

        if (!ex.details().isEmpty()) {
            problem.setProperty("details", ex.details());
        }
        return correlate(problem);
    }

    public ProblemDetail forSecurity(
            HttpStatus status,
            ProblemCode code,
            String detail
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return correlate(problem);
    }

    public ProblemDetail forUnexpected(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
        problem.setProperty("code", ProblemCode.INTERNAL_ERROR.name());
        return correlate(problem);
    }

    public static ProblemDetail correlate(ProblemDetail problem) {
        String requestId = MDC.get(RequestCorrelationFilter.MDC_KEY);
        if (requestId != null) problem.setProperty("requestId", requestId);
        return problem;
    }
}
