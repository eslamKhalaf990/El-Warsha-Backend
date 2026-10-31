package com.warsha.erp.exceptions;

import java.util.List;

/** Input that passes type checks but is still invalid. Produces 400 {@code VALIDATION_FAILED}. */
public class ValidationException extends AppException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }

    public ValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_FAILED, message, List.of(new ApiError.FieldViolation(field, message)));
    }
}
