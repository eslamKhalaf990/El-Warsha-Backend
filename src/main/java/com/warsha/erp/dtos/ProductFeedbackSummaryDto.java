package com.warsha.erp.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductFeedbackSummaryDto {
    private Long productId;
    private String productName;
    private Double averageRating;
    private Long totalReviews;
    private Map<Integer, Long> ratingDistribution; // e.g. {5: 10, 4: 2, 3: 1, 2: 0, 1: 0}
    private List<FeedbackResponse> reviews;
}
