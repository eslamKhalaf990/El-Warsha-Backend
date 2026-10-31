package com.warsha.erp.services;

import com.warsha.erp.exceptions.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPricingTest {

    private static final List<OrderPricing.Line> LINES = List.of(
            new OrderPricing.Line(200, 2, 10),   // 400, product discount 20
            new OrderPricing.Line(150, 1, 0));   // 150

    @Test
    void appliesTheSingleFormula() {
        OrderPricing p = OrderPricing.calculate(LINES, 30, 50, 100);

        assertThat(p.subtotal()).isEqualByComparingTo("550");
        assertThat(p.itemDiscount()).isEqualByComparingTo("20");
        assertThat(p.totalDiscount()).isEqualByComparingTo("50");
        assertThat(p.grandTotal()).isEqualByComparingTo("550");   // 550 - 50 + 50
        assertThat(p.amountDue()).isEqualByComparingTo("450");    // what Orders.TotalPrice stores
    }

    @Test
    void roundsToPiasters() {
        OrderPricing p = OrderPricing.calculate(List.of(new OrderPricing.Line(0.1, 3, 0)), 0, 0, 0);
        assertThat(p.subtotal()).isEqualByComparingTo("0.30");
    }

    @Test
    void fullyPrepaidOrderHasNothingDue() {
        OrderPricing p = OrderPricing.calculate(LINES, 0, 0, 530);
        assertThat(p.amountDue()).isEqualByComparingTo("0");
    }

    @Test
    void downPaymentCannotExceedTotal() {
        assertThatThrownBy(() -> OrderPricing.calculate(LINES, 0, 0, 600))
                .isInstanceOf(ValidationException.class)
                .hasMessage("The down payment (600.00 EGP) can't be more than the order total (530.00 EGP).");
    }

    @Test
    void discountCannotExceedItems() {
        assertThatThrownBy(() -> OrderPricing.calculate(LINES, 540, 0, 0))
                .hasMessage("The discount (560.00 EGP) can't be more than the items total (550.00 EGP).");
    }

    @Test
    void negativeAmountsAreRejected() {
        assertThatThrownBy(() -> OrderPricing.calculate(LINES, -1, 0, 0)).hasMessage("Discount can't be negative.");
        assertThatThrownBy(() -> OrderPricing.calculate(LINES, 0, -1, 0)).hasMessage("Delivery charge can't be negative.");
        assertThatThrownBy(() -> OrderPricing.calculate(LINES, 0, 0, -1)).hasMessage("Down payment can't be negative.");
    }
}
