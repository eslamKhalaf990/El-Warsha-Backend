package com.warsha.erp.exceptions;

/** A requested resource does not exist. Produces 404 {@code NOT_FOUND}. */
public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    /** e.g. {@code new NotFoundException("Order", 12)} → "Order #12 was not found." */
    public NotFoundException(String resource, Object id) {
        this(resource + " #" + id + " was not found.");
    }
}
