package com.warsha.erp.dtos;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
public class CreateOrderRequest {

    private Long customerId;
    private Long bankAccountId;

    private List<OrderItemRequest> items;

    private String paymentMethod;
    private String orderSource;
    private String notes;

    private double downPayment;
    private double delivery;
    private double discount;
    private String promoCode;
    private List<MultipartFile> images;
}
