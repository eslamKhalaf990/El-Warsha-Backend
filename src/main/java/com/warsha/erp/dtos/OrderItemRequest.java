package com.warsha.erp.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItemRequest {

    @NotNull(message = "Choose a product for every order line.")
    private Long productId;

    @NotNull(message = "Enter a quantity for every order line.")
    @Min(value = 1, message = "Quantity must be at least 1.")
    private Integer quantity;

    /** ERP only: overrides the catalogue price for this line. */
    @PositiveOrZero(message = "Price can't be negative.")
    private Double unitPrice;
}
