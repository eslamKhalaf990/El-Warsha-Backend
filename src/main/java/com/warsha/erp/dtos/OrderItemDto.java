package com.warsha.erp.dtos;

import com.warsha.erp.entities.OrderItems;
import com.warsha.erp.entities.ProductVariant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OrderItemDto {
    private Long productId;
    private String productName;
    private String productNameAr;
    private Long variantId;
    private String size;
    private String color;
    private int quantity;
    private double unitPrice;

    public static OrderItemDto of(OrderItems item) {
        ProductVariant v = item.getVariant();
        return new OrderItemDto(
                item.getProduct().getProductID(),
                item.getProduct().getName(),
                item.getProduct().getNameAr(),
                v != null ? v.getId() : null,
                v != null ? v.getSize() : null,
                v != null ? v.getColor() : null,
                item.getQuantity(),
                item.getUnitPrice());
    }
}
