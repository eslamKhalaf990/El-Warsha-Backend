package com.warsha.erp.dtos;

import lombok.Data;

@Data
public class CreateCommentRequest {
    private String commentText;
    private String authorName; // optional if user is authenticated
}
