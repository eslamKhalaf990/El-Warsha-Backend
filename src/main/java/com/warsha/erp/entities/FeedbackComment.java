package com.warsha.erp.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "FeedbackComments")
public class FeedbackComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CommentID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FeedbackID", nullable = false)
    @JsonIgnore
    private CustomerFeedback feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "otp", "otpExpirationTime"})
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User user;

    @Column(name = "AuthorName", nullable = false)
    private String authorName;

    @Column(name = "AuthorRole", nullable = false)
    private String authorRole = "CUSTOMER"; // CUSTOMER, ADMIN

    @Column(name = "CommentText", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String commentText;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (authorRole == null) {
            authorRole = "CUSTOMER";
        }
    }
}
