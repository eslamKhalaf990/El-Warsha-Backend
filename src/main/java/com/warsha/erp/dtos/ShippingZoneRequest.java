package com.warsha.erp.dtos;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Body of POST /shipping and PUT /shipping/{id}. */
@Getter
@Setter
public class ShippingZoneRequest {

    @NotBlank(message = "Enter the governorate name.")
    @Size(max = 100, message = "Governorate name can be at most 100 characters.")
    private String cityName;

    @NotNull(message = "Enter a delivery price of 0 or more.")
    @PositiveOrZero(message = "Enter a delivery price of 0 or more.")
    @Digits(integer = 8, fraction = 2, message = "Delivery price can have at most 2 decimal places.")
    private BigDecimal shippingFee;

    /** Inactive zones are hidden from the store. Defaults to active. */
    private Boolean isActive;
}
