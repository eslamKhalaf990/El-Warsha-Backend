package com.warsha.erp.config;

import com.warsha.erp.entities.OrderStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Lets query parameters use the same spellings as JSON, e.g. {@code ?status=Shipped}. */
@Component
public class OrderStatusParamConverter implements Converter<String, OrderStatus> {

    @Override
    public OrderStatus convert(String source) {
        return source.isBlank() ? null : OrderStatus.from(source);
    }
}
