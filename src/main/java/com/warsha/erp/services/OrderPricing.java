package com.warsha.erp.services;

import com.warsha.erp.exceptions.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The one pricing formula used by every order path (ERP create/edit and store checkout):
 *
 * <pre>
 *   subtotal      = Σ unitPrice × quantity
 *   itemDiscount  = Σ product discount per unit × quantity
 *   totalDiscount = itemDiscount + orderDiscount
 *   grandTotal    = subtotal − totalDiscount + delivery
 *   amountDue     = grandTotal − downPayment      (stored in Orders.TotalPrice)
 * </pre>
 *
 * Orders.TotalPrice keeps its existing meaning, the amount still to collect, so
 * invoices, reports and old orders stay correct.
 */
public record OrderPricing(
        BigDecimal subtotal,
        BigDecimal itemDiscount,
        BigDecimal orderDiscount,
        BigDecimal delivery,
        BigDecimal downPayment
) {

    /** One order line. {@code unitDiscount} is the product's per-unit discount. */
    public record Line(double unitPrice, int quantity, double unitDiscount) {
    }

    public static OrderPricing calculate(List<Line> lines, double orderDiscount, double delivery, double downPayment) {
        requireNotNegative("discount", "Discount", orderDiscount);
        requireNotNegative("delivery", "Delivery charge", delivery);
        requireNotNegative("downPayment", "Down payment", downPayment);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal itemDiscount = BigDecimal.ZERO;
        for (Line line : lines) {
            BigDecimal qty = BigDecimal.valueOf(line.quantity());
            subtotal = subtotal.add(money(line.unitPrice()).multiply(qty));
            itemDiscount = itemDiscount.add(money(line.unitDiscount()).multiply(qty));
        }

        return checked(new OrderPricing(round(subtotal), round(itemDiscount),
                money(orderDiscount), money(delivery), money(downPayment)));
    }

    /** The same order with a different order-level discount (a store promo code), checked again. */
    public OrderPricing withOrderDiscount(BigDecimal orderDiscount) {
        return checked(new OrderPricing(subtotal, itemDiscount, round(orderDiscount), delivery, downPayment));
    }

    private static OrderPricing checked(OrderPricing pricing) {
        if (pricing.totalDiscount().compareTo(pricing.subtotal()) > 0) {
            throw new ValidationException("discount", "The discount (" + pricing.totalDiscount()
                    + " EGP) can't be more than the items total (" + pricing.subtotal() + " EGP).");
        }
        if (pricing.downPayment().compareTo(pricing.grandTotal()) > 0) {
            throw new ValidationException("downPayment", "The down payment (" + pricing.downPayment()
                    + " EGP) can't be more than the order total (" + pricing.grandTotal() + " EGP).");
        }
        return pricing;
    }

    public BigDecimal totalDiscount() {
        return itemDiscount.add(orderDiscount);
    }

    public BigDecimal grandTotal() {
        return subtotal.subtract(totalDiscount()).add(delivery);
    }

    public BigDecimal amountDue() {
        return grandTotal().subtract(downPayment);
    }

    private static void requireNotNegative(String field, String label, double value) {
        if (value < 0) throw new ValidationException(field, label + " can't be negative.");
    }

    private static BigDecimal money(double value) {
        return round(BigDecimal.valueOf(value));
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
