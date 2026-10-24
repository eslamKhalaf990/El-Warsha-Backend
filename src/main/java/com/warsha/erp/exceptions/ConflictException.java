package com.warsha.erp.exceptions;

/** The change collides with existing data, e.g. a duplicate name. Produces 409 {@code CONFLICT}. */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
