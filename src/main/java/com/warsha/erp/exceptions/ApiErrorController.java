package com.warsha.erp.exceptions;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Replaces Spring Boot's default /error page so failures that never reach a controller
 * (unknown URLs, filter errors) still return an {@link ApiError}.
 */
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttr instanceof Integer s ? s : 500;
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object requestId = request.getAttribute(GlobalExceptionHandler.REQUEST_ID_KEY);

        ErrorCode code = GlobalExceptionHandler.codeFor(status);
        ApiError body = ApiError.of(code, null, List.of(),
                uri != null ? uri.toString() : request.getRequestURI(),
                requestId != null ? requestId.toString() : MDC.get(GlobalExceptionHandler.REQUEST_ID_KEY));
        return ResponseEntity.status(code.status()).body(body);
    }
}
