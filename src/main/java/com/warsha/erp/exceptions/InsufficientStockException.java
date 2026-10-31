package com.warsha.erp.exceptions;

/** Not enough stock to fulfil an order line. Produces 409 {@code INSUFFICIENT_STOCK}. */
public class InsufficientStockException extends AppException {

    public InsufficientStockException(String message) {
        super(ErrorCode.INSUFFICIENT_STOCK, message);
    }
}
