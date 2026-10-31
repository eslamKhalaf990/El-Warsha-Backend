package com.warsha.erp.dtos;

import com.warsha.erp.entities.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body of POST /vouchers and PUT /vouchers/{id} (ERP promo codes). */
@Getter
@Setter
public class VoucherRequest {

    @NotBlank(message = "Enter the code customers type.")
    @Size(max = 50, message = "Code can be at most 50 characters.")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Use letters, numbers, - or _ only, no spaces.")
    private String code;

    @NotNull(message = "Choose a percentage or a fixed amount.")
    private DiscountType type;

    @NotNull(message = "Enter the discount.")
    @DecimalMin(value = "0.01", message = "Discount must be more than 0.")
    @Digits(integer = 8, fraction = 2, message = "Discount can have at most 2 decimal places.")
    private BigDecimal discountValue;

    @PositiveOrZero(message = "Minimum order can't be negative.")
    @Digits(integer = 8, fraction = 2, message = "Minimum order can have at most 2 decimal places.")
    private BigDecimal minOrderAmount;

    @Positive(message = "Maximum discount must be more than 0.")
    @Digits(integer = 8, fraction = 2, message = "Maximum discount can have at most 2 decimal places.")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Choose the start date.")
    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean active;

    @Positive(message = "Usage limit must be at least 1.")
    private Integer maxUsageLimit;
}
