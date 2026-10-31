package com.warsha.erp.dtos;

import com.warsha.erp.entities.DiscountType;
import com.warsha.erp.entities.Voucher;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A promo code as the ERP lists it. */
public record VoucherDto(
        Long id,
        String code,
        DiscountType type,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        Integer maxUsageLimit,
        int usageCount) {

    public static VoucherDto from(Voucher v) {
        return new VoucherDto(v.getId(), v.getCode(), v.getType(), v.getDiscountValue(), v.getMinOrderAmount(),
                v.getMaxDiscountAmount(), v.getStartDate(), v.getEndDate(), v.isActive(), v.getMaxUsageLimit(),
                v.getCurrentUsageCount() == null ? 0 : v.getCurrentUsageCount());
    }
}
