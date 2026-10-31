package com.warsha.erp.repository;

import com.warsha.erp.entities.StoreVisitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface StoreVisitorRepository extends JpaRepository<StoreVisitor, String> {

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM StoreVisitors WHERE LastSeenAt < :before", nativeQuery = true)
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
