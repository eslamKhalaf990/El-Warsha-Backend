package com.warsha.erp.exceptions;

/**
 * A valid request that the current state of the data does not allow,
 * e.g. completing a cancelled order. Produces 422 {@code BUSINESS_RULE_VIOLATION}
 * unless a more specific code is given.
 */
public class BusinessRuleException extends AppException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public BusinessRuleException(ErrorCode code, String message) {
        super(code, message);
    }
}
