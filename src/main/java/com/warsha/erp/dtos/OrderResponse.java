package com.warsha.erp.dtos;

import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class OrderResponse {
    private Long orderId;
    private OrderStatus status;
    /** Statuses this order may move to next; empty once Completed or Cancelled. */
    private List<OrderStatus> allowedNextStatuses = List.of();

    /** Total discount: product discounts + {@link #extraDiscount}. */
    private double discount;
    /** The discount typed by staff on top of product discounts; what the edit form should show. */
    private double extraDiscount;
    private double delivery;
    /** Amount still to collect (same as {@link #amountDue}); kept for existing clients. */
    private BigDecimal totalPrice;
    private double downPayment;
    /** Items total − discounts + delivery. */
    private BigDecimal grandTotal;
    /** grandTotal − downPayment: what the courier / final payment must collect. */
    private BigDecimal amountDue;
    private String notes;
    private String orderSource;
    private String paymentMethod;
    private LocalDate orderDate;
    private CustomerDto customer;
    private List<OrderItemDto> orderItems;

    /** Fills totalPrice, amountDue, grandTotal, downPayment and extraDiscount from the stored order. */
    public void applyTotals(Order order) {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        BigDecimal productDiscounts = BigDecimal.ZERO;
        if (order.getItems() != null) {
            for (var item : order.getItems()) {
                BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
                itemsTotal = itemsTotal.add(BigDecimal.valueOf(item.getUnitPrice()).multiply(qty));
                Double unitDiscount = item.getProduct() != null ? item.getProduct().getDiscount() : null;
                if (unitDiscount != null) {
                    productDiscounts = productDiscounts.add(BigDecimal.valueOf(unitDiscount).multiply(qty));
                }
            }
        }
        BigDecimal extra = BigDecimal.valueOf(order.getDiscount()).subtract(productDiscounts);
        setExtraDiscount(extra.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).doubleValue());
        BigDecimal due = BigDecimal.valueOf(order.getTotalPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grand = itemsTotal
                .subtract(BigDecimal.valueOf(order.getDiscount()))
                .add(BigDecimal.valueOf(order.getDeliveryCharge()))
                .setScale(2, RoundingMode.HALF_UP);
        setTotalPrice(due);
        setAmountDue(due);
        setGrandTotal(grand);
        setDownPayment(grand.subtract(due).doubleValue());
    }
}