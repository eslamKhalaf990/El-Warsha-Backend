package com.warsha.erp.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class VoucherValidationResponse {
    private boolean valid;
    private String message;
    private BigDecimal discountAmount;
    private String code;
    /** Why the code was refused (EMPTY, NOT_FOUND, INACTIVE, NOT_STARTED, EXPIRED, USED_UP, MIN_ORDER); null when valid. */
    private String reason;
}