package com.warsha.erp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warsha.erp.exceptions.ApiError;
import com.warsha.erp.exceptions.ErrorCode;
import com.warsha.erp.exceptions.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Writes 401 / 403 responses from the security filter chain as {@link ApiError},
 * so they look exactly like errors thrown from controllers.
 */
@Component
public class RestAuthErrorHandlers {

    private final ObjectMapper objectMapper;

    public RestAuthErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> write(request, response, ErrorCode.UNAUTHENTICATED);
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(request, response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        ApiError body = ApiError.of(code, null, List.of(), request.getRequestURI(),
                MDC.get(GlobalExceptionHandler.REQUEST_ID_KEY));
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
