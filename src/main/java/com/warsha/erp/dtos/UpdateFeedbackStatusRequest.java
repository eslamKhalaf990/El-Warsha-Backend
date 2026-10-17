package com.warsha.erp.dtos;

import lombok.Data;

@Data
public class UpdateFeedbackStatusRequest {
    private String status; // PENDING, APPROVED, REJECTED, RESOLVED
    private Boolean isPublic;
    private Boolean isFeatured;
}
