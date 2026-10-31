package com.warsha.erp.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in every {@link ApiError}.
 * Clients branch on {@code code}; {@code message} is meant to be shown to the user.
 */
public enum ErrorCode {

    // 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Some fields are missing or invalid."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read. Please check the data you sent."),
    // 401
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Please log in to continue."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Incorrect username or password."),
    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have permission to do this."),
    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested item was not found."),
    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This action is not supported here."),
    // 409
    CONFLICT(HttpStatus.CONFLICT, "This change conflicts with existing data."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "There is not enough stock for this item."),
    // 413
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large."),
    // 422
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "This action is not allowed in the current state."),
    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
