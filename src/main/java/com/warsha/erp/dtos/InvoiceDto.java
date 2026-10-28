package com.warsha.erp.dtos;

import com.warsha.erp.entities.Invoice;
import com.warsha.erp.entities.Order;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.stream.Collectors;

@Setter
@Getter
public class InvoiceDto {
    private Long id;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private String notes;
    private OrderResponse order; // full order response

    public InvoiceDto(Invoice invoice) {
        this.id = invoice.getId();
        this.invoiceNumber = invoice.getInvoiceNumber();
        this.invoiceDate = invoice.getIssuedDate();
        this.notes = invoice.getNotes();

        Order orderEntity = invoice.getOrder();
        OrderResponse orderDto = new OrderResponse();
        orderDto.setOrderId(orderEntity.getId());
        orderDto.setStatus(orderEntity.getStatus());
        orderDto.setOrderDate(orderEntity.getOrderDate());

        // customer
        CustomerDto customerDto = new CustomerDto();

        customerDto.setCustomerId(orderEntity.getCustomer().getId());
        customerDto.setFullName(orderEntity.getCustomer().getFullName());
        customerDto.setCity(orderEntity.getCustomer().getCity());
        customerDto.setSecondaryPhone(orderEntity.getCustomer().getSecondaryPhone());
        customerDto.setAddress(orderEntity.getCustomer().getAddress());
        customerDto.setPhone(orderEntity.getCustomer().getPhone());

        orderDto.setCustomer(customerDto);

        // items
        orderDto.setOrderItems(
                orderEntity.getItems().stream()
                        .map(OrderItemDto::of)
                        .collect(Collectors.toList())
        );

        this.order = orderDto;
    }
}