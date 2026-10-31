package com.warsha.erp.exceptions;

import java.util.List;

/**
 * Base class for every expected, user-facing failure. The message must be safe and
 * friendly enough to show directly in the UI.
 */
public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiError.FieldViolation> details;

    public AppException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public AppException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public AppException(ErrorCode code, String message, List<ApiError.FieldViolation> details) {
        super(message);
        this.code = code;
        this.details = details != null ? details : List.of();
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<ApiError.FieldViolation> getDetails() {
        return details;
    }
}
