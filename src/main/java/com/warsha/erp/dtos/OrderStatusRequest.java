package com.warsha.erp.dtos;

import com.warsha.erp.entities.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderStatusRequest {

    @NotNull(message = "Choose the new status for the order.")
    private OrderStatus status;

    /** Required when moving to Completed: the account that received the final payment. */
    private Long bankAccountId;
}
