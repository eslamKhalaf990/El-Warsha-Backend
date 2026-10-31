package com.warsha.erp.controllers;

import com.warsha.erp.config.CustomerUserDetails;
import com.warsha.erp.dtos.*;
import com.warsha.erp.services.CustomerFeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/feedbacks")
public class CustomerFeedbackController {

    private final CustomerFeedbackService feedbackService;
    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    @Autowired
    public CustomerFeedbackController(CustomerFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(logFormat);
    }

    /**
     * Submit new customer feedback.
     * Can be submitted by logged-in customers (using Bearer token) or guest users.
     */
    @PostMapping
    public ResponseEntity<?> submitFeedback(@RequestBody CreateFeedbackRequest request,
                                            Authentication authentication) {
        System.out.println("[" + getTimestamp() + "] INFO: Feedback submission received");
        try {
            Long customerId = null;
            if (authentication != null && authentication.getPrincipal() instanceof CustomerUserDetails customerUserDetails) {
                customerId = customerUserDetails.getId();
                System.out.println("[" + getTimestamp() + "] INFO: Feedback linked to authenticated Customer ID: " + customerId);
            }

            FeedbackResponse response = feedbackService.submitFeedback(request, customerId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            System.out.println("[" + getTimestamp() + "] WARN: Validation error in feedback submission: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            System.out.println("[" + getTimestamp() + "] ERROR: Error creating feedback: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to submit feedback: " + e.getMessage());
        }
    }

    /**
     * Get approved public feedbacks / testimonials for landing page and reviews section.
     */
    @GetMapping("/public")
    public ResponseEntity<List<FeedbackResponse>> getPublicFeedbacks(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "false") Boolean featuredOnly) {
        System.out.println("[" + getTimestamp() + "] INFO: Public request for approved feedbacks (type=" + type + ", featuredOnly=" + featuredOnly + ")");
        return ResponseEntity.ok(feedbackService.getPublicFeedbacks(type, featuredOnly));
    }

    /**
     * Get approved reviews and rating breakdown for a specific product.
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getProductReviews(@PathVariable Long productId) {
        System.out.println("[" + getTimestamp() + "] INFO: Request for product reviews: product ID: " + productId);
        try {
            ProductFeedbackSummaryDto summary = feedbackService.getProductFeedbackSummary(productId);
            return ResponseEntity.ok(summary);
        } catch (RuntimeException e) {
            System.out.println("[" + getTimestamp() + "] WARN: Product not found for reviews: " + productId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * Get all feedbacks submitted by the logged-in customer.
     */
    @GetMapping("/customer")
    public ResponseEntity<?> getCustomerFeedbacks(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomerUserDetails customerUserDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Only authenticated customers can view their feedback history.");
        }

        Long customerId = customerUserDetails.getId();
        System.out.println("[" + getTimestamp() + "] INFO: Customer ID " + customerId + " viewing their feedback history");
        return ResponseEntity.ok(feedbackService.getCustomerFeedbacks(customerId));
    }

    /**
     * Get feedback by ID (including comments thread).
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getFeedbackById(@PathVariable Long id) {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching feedback details for ID: " + id);
        try {
            return ResponseEntity.ok(feedbackService.getFeedbackById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * Add a comment or reply to an existing feedback thread.
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id,
                                        @RequestBody CreateCommentRequest request,
                                        Authentication authentication) {
        System.out.println("[" + getTimestamp() + "] INFO: Adding comment to feedback ID: " + id);
        try {
            Long customerId = null;
            String adminUsername = null;

            if (authentication != null) {
                if (authentication.getPrincipal() instanceof CustomerUserDetails customerUserDetails) {
                    customerId = customerUserDetails.getId();
                } else if (authentication.getPrincipal() instanceof UserDetails userDetails) {
                    adminUsername = userDetails.getUsername();
                }
            }

            FeedbackCommentDto commentDto = feedbackService.addComment(id, request, customerId, adminUsername);
            return ResponseEntity.status(HttpStatus.CREATED).body(commentDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * ERP Admin: Search and filter all feedbacks.
     */
    @GetMapping
    public ResponseEntity<List<FeedbackResponse>> getAllFeedbacks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search) {

        System.out.println("[" + getTimestamp() + "] INFO: Admin listing feedbacks");
        List<FeedbackResponse> list = feedbackService.searchFeedbacks(
                status, type, rating, productId, startDate, endDate, search);
        return ResponseEntity.ok(list);
    }

    /**
     * ERP Admin: Moderate feedback (Approve / Reject / Resolve / Feature / Public).
     */
    @PutMapping("/{id}/status")
    public FeedbackResponse updateFeedbackStatus(@PathVariable Long id,
                                                 @RequestBody UpdateFeedbackStatusRequest request) {
        System.out.println("[" + getTimestamp() + "] INFO: Admin updating status of feedback ID: " + id);
        return feedbackService.updateFeedbackStatus(id, request);
    }

    /**
     * ERP Admin: Submit official reply to customer feedback.
     */
    @PostMapping("/{id}/reply")
    public FeedbackResponse replyToFeedback(@PathVariable Long id,
                                            @RequestBody AdminReplyRequest request,
                                            Authentication authentication) {
        String adminUsername = (authentication != null) ? authentication.getName() : "Admin";
        System.out.println("[" + getTimestamp() + "] INFO: Admin " + adminUsername + " replying to feedback ID: " + id);
        return feedbackService.adminReply(id, request.getReply(), adminUsername);
    }

    /**
     * ERP Admin: Delete feedback.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFeedback(@PathVariable Long id) {
        System.out.println("[" + getTimestamp() + "] WARN: Admin deleting feedback ID: " + id);
        feedbackService.deleteFeedback(id);
        return ResponseEntity.noContent().build();
    }
}
