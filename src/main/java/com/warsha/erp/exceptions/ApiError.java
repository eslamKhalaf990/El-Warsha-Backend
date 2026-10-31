package com.warsha.erp.exceptions;

import java.time.Instant;
import java.util.List;

/**
 * The single error body returned by every endpoint.
 *
 * <pre>
 * {
 *   "status": 409,
 *   "code": "INSUFFICIENT_STOCK",
 *   "message": "Not enough stock for 'Black bracelet' (available: 2, requested: 5).",
 *   "details": [],
 *   "path": "/orders",
 *   "requestId": "8f2c1a7e",
 *   "timestamp": "2026-10-24T12:00:00Z"
 * }
 * </pre>
 */
public record ApiError(
        int status,
        String code,
        String message,
        List<FieldViolation> details,
        String path,
        String requestId,
        Instant timestamp
) {

    /** One invalid input field, e.g. {@code {"field": "items[0].quantity", "message": "must be greater than 0"}}. */
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, List<FieldViolation> details, String path, String requestId) {
        return new ApiError(
                code.status().value(),
                code.name(),
                message != null && !message.isBlank() ? message : code.defaultMessage(),
                details != null ? details : List.of(),
                path,
                requestId,
                Instant.now()
        );
    }
}
