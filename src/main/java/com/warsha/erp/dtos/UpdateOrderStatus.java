package com.warsha.erp.dtos;

import com.warsha.erp.entities.OrderStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatus {
    private OrderStatus status;
    private Long bankAccountId;
}
