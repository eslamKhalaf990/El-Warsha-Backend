package com.warsha.erp.repository;

import com.warsha.erp.entities.CustomerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, Long> {

    List<CustomerFeedback> findAllByOrderByCreatedAtDesc();

    List<CustomerFeedback> findByIsPublicTrueAndStatusOrderByCreatedAtDesc(String status);

    List<CustomerFeedback> findByIsFeaturedTrueAndIsPublicTrueAndStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT f FROM CustomerFeedback f WHERE f.product.ProductID = :productId AND f.isPublic = true AND f.status = :status ORDER BY f.createdAt DESC")
    List<CustomerFeedback> findProductPublicApprovedFeedbacks(@Param("productId") Long productId, @Param("status") String status);

    @Query("SELECT f FROM CustomerFeedback f WHERE f.customer.id = :customerId ORDER BY f.createdAt DESC")
    List<CustomerFeedback> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId);

    List<CustomerFeedback> findByStatusOrderByCreatedAtDesc(String status);

    List<CustomerFeedback> findByFeedbackTypeOrderByCreatedAtDesc(String feedbackType);

    @Query("SELECT f FROM CustomerFeedback f WHERE " +
            "(:status IS NULL OR f.status = :status) AND " +
            "(:type IS NULL OR f.feedbackType = :type) AND " +
            "(:rating IS NULL OR f.rating = :rating) AND " +
            "(:productId IS NULL OR f.product.ProductID = :productId) AND " +
            "(:startDate IS NULL OR f.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR f.createdAt <= :endDate) AND " +
            "(:search IS NULL OR LOWER(f.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(f.customerEmail) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(f.subject) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(f.comment) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY f.createdAt DESC")
    List<CustomerFeedback> searchFeedbacks(
            @Param("status") String status,
            @Param("type") String type,
            @Param("rating") Integer rating,
            @Param("productId") Long productId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("search") String search);
}
