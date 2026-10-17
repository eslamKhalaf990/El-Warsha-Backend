package com.warsha.erp.dtos;

import com.warsha.erp.entities.FeedbackComment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeedbackCommentDto {
    private Long commentId;
    private Long feedbackId;
    private Long customerId;
    private Long userId;
    private String authorName;
    private String authorRole;
    private String commentText;
    private LocalDateTime createdAt;

    public static FeedbackCommentDto fromEntity(FeedbackComment comment) {
        if (comment == null) {
            return null;
        }
        FeedbackCommentDto dto = new FeedbackCommentDto();
        dto.setCommentId(comment.getId());
        dto.setFeedbackId(comment.getFeedback() != null ? comment.getFeedback().getId() : null);
        dto.setCustomerId(comment.getCustomer() != null ? comment.getCustomer().getId() : null);
        dto.setUserId(comment.getUser() != null ? comment.getUser().getId() : null);
        dto.setAuthorName(comment.getAuthorName());
        dto.setAuthorRole(comment.getAuthorRole());
        dto.setCommentText(comment.getCommentText());
        dto.setCreatedAt(comment.getCreatedAt());
        return dto;
    }
}
