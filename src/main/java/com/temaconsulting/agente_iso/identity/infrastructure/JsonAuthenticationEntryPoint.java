package com.temaconsulting.agente_iso.identity.infrastructure;

import com.temaconsulting.agente_iso.shared.api.ProblemDetailsFactory;
import com.temaconsulting.agente_iso.shared.application.ProblemCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailsFactory problems;
    private final JsonMapper jsonMapper;

    public JsonAuthenticationEntryPoint(
            ProblemDetailsFactory problems,
            JsonMapper jsonMapper
    ) {
        this.problems = problems;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException ex
    ) throws IOException {
        var problem = problems.forSecurity(
                HttpStatus.UNAUTHORIZED,
                ProblemCode.UNAUTHORIZED,
                "No autenticado"
        );
        problem.setInstance(java.net.URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
