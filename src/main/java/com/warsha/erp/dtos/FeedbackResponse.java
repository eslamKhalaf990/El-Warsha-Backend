package com.warsha.erp.dtos;

import com.warsha.erp.entities.CustomerFeedback;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class FeedbackResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Long productId;
    private String productName;
    private Long orderId;
    private String feedbackType;
    private String subject;
    private String comment;
    private Integer rating;
    private String status;
    private Boolean isPublic;
    private Boolean isFeatured;
    private String adminReply;
    private String repliedBy;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<FeedbackCommentDto> comments;

    public static FeedbackResponse fromEntity(CustomerFeedback feedback) {
        if (feedback == null) {
            return null;
        }

        FeedbackResponse dto = new FeedbackResponse();
        dto.setId(feedback.getId());
        dto.setCustomerId(feedback.getCustomer() != null ? feedback.getCustomer().getId() : null);
        dto.setCustomerName(feedback.getCustomerName());
        dto.setCustomerEmail(feedback.getCustomerEmail());
        dto.setCustomerPhone(feedback.getCustomerPhone());

        if (feedback.getProduct() != null) {
            dto.setProductId(feedback.getProduct().getProductID());
            dto.setProductName(feedback.getProduct().getName());
        }

        if (feedback.getOrder() != null) {
            dto.setOrderId(feedback.getOrder().getId());
        }

        dto.setFeedbackType(feedback.getFeedbackType());
        dto.setSubject(feedback.getSubject());
        dto.setComment(feedback.getComment());
        dto.setRating(feedback.getRating());
        dto.setStatus(feedback.getStatus());
        dto.setIsPublic(feedback.getIsPublic());
        dto.setIsFeatured(feedback.getIsFeatured());
        dto.setAdminReply(feedback.getAdminReply());
        dto.setRepliedBy(feedback.getRepliedBy());
        dto.setRepliedAt(feedback.getRepliedAt());
        dto.setCreatedAt(feedback.getCreatedAt());
        dto.setUpdatedAt(feedback.getUpdatedAt());

        if (feedback.getComments() != null && !feedback.getComments().isEmpty()) {
            dto.setComments(feedback.getComments().stream()
                    .map(FeedbackCommentDto::fromEntity)
                    .collect(Collectors.toList()));
        } else {
            dto.setComments(Collections.emptyList());
        }

        return dto;
    }
}
