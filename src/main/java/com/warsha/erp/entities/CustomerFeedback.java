package com.warsha.erp.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "CustomerFeedbacks")
public class CustomerFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FeedbackID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "otp", "otpExpirationTime"})
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProductID")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OrderID")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "invoice", "items"})
    private Order order;

    @Column(name = "CustomerName", nullable = false)
    private String customerName;

    @Column(name = "CustomerEmail")
    private String customerEmail;

    @Column(name = "CustomerPhone")
    private String customerPhone;

    @Column(name = "FeedbackType", nullable = false)
    private String feedbackType = "GENERAL"; // GENERAL, SERVICE, PRODUCT, ORDER, SUGGESTION, COMPLAINT

    @Column(name = "Subject")
    private String subject;

    @Column(name = "Comment", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String comment;

    @Column(name = "Rating", nullable = false)
    private Integer rating;

    @Column(name = "Status", nullable = false)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED, RESOLVED

    @Column(name = "IsPublic", nullable = false)
    private Boolean isPublic = false;

    @Column(name = "IsFeatured", nullable = false)
    private Boolean isFeatured = false;

    @Column(name = "AdminReply", columnDefinition = "NVARCHAR(MAX)")
    private String adminReply;

    @Column(name = "RepliedBy")
    private String repliedBy;

    @Column(name = "RepliedAt")
    private LocalDateTime repliedAt;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<FeedbackComment> comments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (feedbackType == null) {
            feedbackType = "GENERAL";
        }
        if (isPublic == null) {
            isPublic = false;
        }
        if (isFeatured == null) {
            isFeatured = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
