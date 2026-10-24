package com.warsha.erp.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Body for creating or editing an order (ERP) and for store checkout.
 * Store checkout ignores customerId, bankAccountId, discount, delivery and unit prices:
 * the customer comes from the token and the rest is decided by the server.
 */
@Getter
@Setter
public class OrderRequest {

    /** ERP only. */
    private Long customerId;

    /** ERP only: the account that received the down payment. Defaults to the main account. */
    private Long bankAccountId;

    @NotEmpty(message = "Add at least one product to the order.")
    private List<@Valid OrderItemRequest> items;

    @NotBlank(message = "Choose a payment method.")
    @Size(max = 50, message = "Payment method must be at most 50 characters.")
    private String paymentMethod;

    @NotBlank(message = "Choose where the order came from.")
    @Size(max = 50, message = "Order source must be at most 50 characters.")
    private String orderSource;

    @Size(max = 1000, message = "Notes must be at most 1000 characters.")
    private String notes;

    @PositiveOrZero(message = "Down payment can't be negative.")
    private double downPayment;

    /** ERP only. */
    @PositiveOrZero(message = "Delivery charge can't be negative.")
    private double delivery;

    /** ERP only: extra discount on top of product discounts. */
    @PositiveOrZero(message = "Discount can't be negative.")
    private double discount;

    private String promoCode;
}
