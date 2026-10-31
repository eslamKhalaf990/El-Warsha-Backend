package com.warsha.erp.dtos;

import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.OrderItems;
import com.warsha.erp.entities.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderResponseTest {

    @Test
    void splitsTotalsAndExtraDiscount() {
        Product product = new Product();
        product.setDiscount(10.0);
        OrderItems item = new OrderItems();
        item.setProduct(product);
        item.setUnitPrice(200.0);
        item.setQuantity(2);

        Order order = new Order();
        order.setItems(List.of(item));
        order.setDiscount(50.0);       // 20 from the product + 30 typed by staff
        order.setDeliveryCharge(40.0);
        order.setTotalPrice(290.0);    // amount still due

        OrderResponse dto = new OrderResponse();
        dto.applyTotals(order);

        assertThat(dto.getGrandTotal()).isEqualByComparingTo("390");   // 400 - 50 + 40
        assertThat(dto.getAmountDue()).isEqualByComparingTo("290");
        assertThat(dto.getDownPayment()).isEqualTo(100.0);
        assertThat(dto.getExtraDiscount()).isEqualTo(30.0);
    }

    @Test
    void extraDiscountNeverNegative() {
        Product product = new Product();
        product.setDiscount(10.0);
        OrderItems item = new OrderItems();
        item.setProduct(product);
        item.setUnitPrice(100.0);
        item.setQuantity(1);

        Order order = new Order();
        order.setItems(List.of(item));
        order.setDiscount(0.0);        // product discount was added after the order
        order.setDeliveryCharge(0.0);
        order.setTotalPrice(100.0);

        OrderResponse dto = new OrderResponse();
        dto.applyTotals(order);
        assertThat(dto.getExtraDiscount()).isZero();
    }
}
