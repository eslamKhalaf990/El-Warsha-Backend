package com.warsha.erp.exceptions;

import java.util.List;

/** The change collides with existing data, e.g. a duplicate name. Produces 409 {@code CONFLICT}. */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    /** A conflict caused by one input field, so forms can show it under that field. */
    public ConflictException(String field, String message) {
        super(ErrorCode.CONFLICT, message, List.of(new ApiError.FieldViolation(field, message)));
    }
}
