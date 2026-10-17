package com.warsha.erp.repository;

import com.warsha.erp.entities.FeedbackComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackCommentRepository extends JpaRepository<FeedbackComment, Long> {

    @Query("SELECT c FROM FeedbackComment c WHERE c.feedback.id = :feedbackId ORDER BY c.createdAt ASC")
    List<FeedbackComment> findByFeedbackIdOrderByCreatedAtAsc(@Param("feedbackId") Long feedbackId);
}
