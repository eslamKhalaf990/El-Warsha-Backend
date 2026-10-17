package com.warsha.erp.dtos;

import lombok.Data;

@Data
public class CreateFeedbackRequest {
    private String feedbackType; // GENERAL, SERVICE, PRODUCT, ORDER, SUGGESTION, COMPLAINT
    private Integer rating; // 1 to 5
    private String subject;
    private String comment;
    private Long productId; // optional
    private Long orderId; // optional

    // For guest submission or custom contact information
    private String customerName;
    private String customerEmail;
    private String customerPhone;
}
