package com.warsha.erp.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link OrderStatus} as its label in Orders.Status. */
@Converter
public class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(OrderStatus status) {
        return status == null ? null : status.label();
    }

    @Override
    public OrderStatus convertToEntityAttribute(String value) {
        return value == null ? null : OrderStatus.from(value);
    }
}
