package com.warsha.erp.services;

import com.warsha.erp.dtos.*;
import com.warsha.erp.entities.*;
import com.warsha.erp.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomerFeedbackService {

    private final CustomerFeedbackRepository feedbackRepository;
    private final FeedbackCommentRepository commentRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    @Autowired
    public CustomerFeedbackService(CustomerFeedbackRepository feedbackRepository,
                                   FeedbackCommentRepository commentRepository,
                                   CustomerRepository customerRepository,
                                   ProductRepository productRepository,
                                   OrderRepository orderRepository,
                                   UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.commentRepository = commentRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(logFormat);
    }

    @Transactional
    public FeedbackResponse submitFeedback(CreateFeedbackRequest request, Long authenticatedCustomerId) {
        System.out.println("[" + getTimestamp() + "] INFO: Submitting new customer feedback");

        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("Rating is required and must be between 1 and 5.");
        }

        if (request.getComment() == null || request.getComment().trim().isEmpty()) {
            throw new IllegalArgumentException("Feedback comment is required.");
        }

        CustomerFeedback feedback = new CustomerFeedback();
        feedback.setRating(request.getRating());
        feedback.setComment(request.getComment().trim());
        feedback.setSubject(request.getSubject() != null ? request.getSubject().trim() : null);

        String type = (request.getFeedbackType() != null && !request.getFeedbackType().trim().isEmpty())
                ? request.getFeedbackType().trim().toUpperCase()
                : "GENERAL";
        feedback.setFeedbackType(type);

        // Associate authenticated customer or guest contact details
        if (authenticatedCustomerId != null) {
            Customer customer = customerRepository.findById(authenticatedCustomerId)
                    .orElseThrow(() -> new RuntimeException("Customer not found with ID: " + authenticatedCustomerId));
            feedback.setCustomer(customer);
            feedback.setCustomerName(customer.getFullName());
            feedback.setCustomerEmail(customer.getEmail());
            feedback.setCustomerPhone(customer.getPhone());
        } else {
            // Guest submission
            if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()) {
                throw new IllegalArgumentException("Customer name is required for guest feedback submission.");
            }
            feedback.setCustomerName(request.getCustomerName().trim());
            feedback.setCustomerEmail(request.getCustomerEmail() != null ? request.getCustomerEmail().trim() : null);
            feedback.setCustomerPhone(request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : null);
        }

        // Optional Product link
        if (request.getProductId() != null) {
            Product product = productRepository.findById(request.getProductId()).orElse(null);
            if (product != null) {
                feedback.setProduct(product);
            }
        }

        // Optional Order link
        if (request.getOrderId() != null) {
            Order order = orderRepository.findById(request.getOrderId()).orElse(null);
            if (order != null) {
                feedback.setOrder(order);
            }
        }

        feedback.setStatus("PENDING");
        feedback.setIsPublic(false);
        feedback.setIsFeatured(false);

        CustomerFeedback saved = feedbackRepository.save(feedback);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Feedback created with ID: " + saved.getId());

        return FeedbackResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getPublicFeedbacks(String type, Boolean featuredOnly) {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching public approved feedbacks (type=" + type + ", featuredOnly=" + featuredOnly + ")");

        List<CustomerFeedback> list;
        if (Boolean.TRUE.equals(featuredOnly)) {
            list = feedbackRepository.findByIsFeaturedTrueAndIsPublicTrueAndStatusOrderByCreatedAtDesc("APPROVED");
        } else if (type != null && !type.trim().isEmpty()) {
            list = feedbackRepository.findByIsPublicTrueAndStatusOrderByCreatedAtDesc("APPROVED")
                    .stream()
                    .filter(f -> f.getFeedbackType().equalsIgnoreCase(type.trim()))
                    .collect(Collectors.toList());
        } else {
            list = feedbackRepository.findByIsPublicTrueAndStatusOrderByCreatedAtDesc("APPROVED");
        }

        return list.stream().map(FeedbackResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductFeedbackSummaryDto getProductFeedbackSummary(Long productId) {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching product feedback summary for product ID: " + productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + productId));

        List<CustomerFeedback> reviews = feedbackRepository
                .findProductPublicApprovedFeedbacks(productId, "APPROVED");

        long totalReviews = reviews.size();
        double avgRating = totalReviews > 0
                ? reviews.stream().mapToInt(CustomerFeedback::getRating).average().orElse(0.0)
                : 0.0;

        // Round to 1 decimal place
        avgRating = Math.round(avgRating * 10.0) / 10.0;

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            int currentStar = star;
            long count = reviews.stream().filter(r -> r.getRating() != null && r.getRating() == currentStar).count();
            distribution.put(star, count);
        }

        List<FeedbackResponse> reviewResponses = reviews.stream()
                .map(FeedbackResponse::fromEntity)
                .collect(Collectors.toList());

        return new ProductFeedbackSummaryDto(
                product.getProductID(),
                product.getName(),
                avgRating,
                totalReviews,
                distribution,
                reviewResponses
        );
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getCustomerFeedbacks(Long customerId) {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching feedbacks for customer ID: " + customerId);
        return feedbackRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(FeedbackResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FeedbackResponse getFeedbackById(Long id) {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching feedback ID: " + id);
        CustomerFeedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found with ID: " + id));
        return FeedbackResponse.fromEntity(feedback);
    }

    @Transactional
    public FeedbackCommentDto addComment(Long feedbackId,
                                         CreateCommentRequest request,
                                         Long customerId,
                                         String adminUsername) {
        System.out.println("[" + getTimestamp() + "] INFO: Adding comment to feedback ID: " + feedbackId);

        if (request.getCommentText() == null || request.getCommentText().trim().isEmpty()) {
            throw new IllegalArgumentException("Comment text cannot be empty.");
        }

        CustomerFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new RuntimeException("Feedback not found with ID: " + feedbackId));

        FeedbackComment comment = new FeedbackComment();
        comment.setFeedback(feedback);
        comment.setCommentText(request.getCommentText().trim());

        if (adminUsername != null) {
            comment.setAuthorRole("ADMIN");
            comment.setAuthorName(adminUsername);
            userRepository.findByUsername(adminUsername).ifPresent(comment::setUser);
        } else if (customerId != null) {
            Customer customer = customerRepository.findById(customerId).orElse(null);
            comment.setCustomer(customer);
            comment.setAuthorRole("CUSTOMER");
            comment.setAuthorName(customer != null ? customer.getFullName() : "Customer");
        } else {
            comment.setAuthorRole("CUSTOMER");
            comment.setAuthorName(request.getAuthorName() != null && !request.getAuthorName().trim().isEmpty()
                    ? request.getAuthorName().trim()
                    : "Guest");
        }

        FeedbackComment savedComment = commentRepository.save(comment);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Comment added with ID: " + savedComment.getId());

        return FeedbackCommentDto.fromEntity(savedComment);
    }

    @Transactional
    public FeedbackResponse updateFeedbackStatus(Long id, UpdateFeedbackStatusRequest request) {
        System.out.println("[" + getTimestamp() + "] INFO: Updating status of feedback ID: " + id);

        CustomerFeedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found with ID: " + id));

        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            feedback.setStatus(request.getStatus().trim().toUpperCase());
        }

        if (request.getIsPublic() != null) {
            feedback.setIsPublic(request.getIsPublic());
        }

        if (request.getIsFeatured() != null) {
            feedback.setIsFeatured(request.getIsFeatured());
        }

        CustomerFeedback updated = feedbackRepository.save(feedback);
        return FeedbackResponse.fromEntity(updated);
    }

    @Transactional
    public FeedbackResponse adminReply(Long id, String replyText, String adminUsername) {
        System.out.println("[" + getTimestamp() + "] INFO: Admin replying to feedback ID: " + id);

        if (replyText == null || replyText.trim().isEmpty()) {
            throw new IllegalArgumentException("Reply message cannot be empty.");
        }

        CustomerFeedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found with ID: " + id));

        feedback.setAdminReply(replyText.trim());
        feedback.setRepliedBy(adminUsername != null ? adminUsername : "Admin");
        feedback.setRepliedAt(LocalDateTime.now());

        // Also add comment entry in thread
        FeedbackComment comment = new FeedbackComment();
        comment.setFeedback(feedback);
        comment.setAuthorRole("ADMIN");
        comment.setAuthorName(adminUsername != null ? adminUsername : "Admin");
        comment.setCommentText(replyText.trim());
        userRepository.findByUsername(adminUsername).ifPresent(comment::setUser);
        commentRepository.save(comment);

        CustomerFeedback updated = feedbackRepository.save(feedback);
        return FeedbackResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> searchFeedbacks(String status,
                                                  String type,
                                                  Integer rating,
                                                  Long productId,
                                                  LocalDate startDate,
                                                  LocalDate endDate,
                                                  String search) {
        System.out.println("[" + getTimestamp() + "] INFO: ERP Admin searching feedbacks");

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        String formattedStatus = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : null;
        String formattedType = (type != null && !type.trim().isEmpty()) ? type.trim().toUpperCase() : null;
        String formattedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        List<CustomerFeedback> results = feedbackRepository.searchFeedbacks(
                formattedStatus,
                formattedType,
                rating,
                productId,
                startDateTime,
                endDateTime,
                formattedSearch
        );

        return results.stream().map(FeedbackResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public void deleteFeedback(Long id) {
        System.out.println("[" + getTimestamp() + "] WARN: Deleting feedback ID: " + id);
        CustomerFeedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found with ID: " + id));
        feedbackRepository.delete(feedback);
    }
}
