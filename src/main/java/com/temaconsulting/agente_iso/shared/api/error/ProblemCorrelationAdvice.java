package com.temaconsulting.agente_iso.shared.api.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Applies correlation to ProblemDetails built by every module's exception handler. */
@RestControllerAdvice
public class ProblemCorrelationAdvice implements ResponseBodyAdvice<Object> {
    private static final Logger log = LoggerFactory.getLogger(ProblemCorrelationAdvice.class);

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                         Class<? extends HttpMessageConverter<?>> converterType,
                                         ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem) {
            ProblemDetailsFactory.correlate(problem);
            log.warn("api_error status={} code={} requestId={}", problem.getStatus(),
                    problem.getProperties() == null ? null : problem.getProperties().get("code"),
                    problem.getProperties() == null ? null : problem.getProperties().get("requestId"));
        }
        return body;
    }
}
