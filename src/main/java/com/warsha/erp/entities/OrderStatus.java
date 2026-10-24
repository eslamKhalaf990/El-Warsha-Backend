package com.warsha.erp.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.warsha.erp.exceptions.ValidationException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Order lifecycle: Pending → Processing → Shipped → Completed.
 * An order may move forward (skipping steps is allowed) and may be Cancelled
 * at any point before it is Completed. Completed and Cancelled are final.
 *
 * Stored in the DB and sent over JSON as the label ("Pending", ...), the same
 * values the apps and native reporting queries already use.
 */
public enum OrderStatus {
    PENDING("Pending"),
    PROCESSING("Processing"),
    SHIPPED("Shipped"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Items, prices and payments can only be changed before the order ships. */
    public boolean isEditable() {
        return this == PENDING || this == PROCESSING;
    }

    public boolean canMoveTo(OrderStatus target) {
        if (isFinal() || target == this) return false;
        return target == CANCELLED || target.ordinal() > ordinal();
    }

    public List<OrderStatus> allowedNext() {
        return Arrays.stream(values()).filter(this::canMoveTo).toList();
    }

    /** Accepts the label or the enum name, case-insensitively ("shipped", "SHIPPED", "Shipped"). */
    @JsonCreator
    public static OrderStatus from(String value) {
        if (value != null) {
            String v = value.trim();
            if (v.equalsIgnoreCase("Canceled")) return CANCELLED;
            for (OrderStatus s : values()) {
                if (s.label.equalsIgnoreCase(v) || s.name().equalsIgnoreCase(v)) return s;
            }
        }
        String allowed = Arrays.stream(values()).map(OrderStatus::label).collect(Collectors.joining(", "));
        throw new ValidationException("status",
                "'" + value + "' is not a valid order status. Use one of: " + allowed + ".");
    }

    @Override
    public String toString() {
        return label;
    }

    /** Lower-case label for sentences, e.g. "is already shipped". */
    public String lower() {
        return label.toLowerCase(Locale.ROOT);
    }
}
