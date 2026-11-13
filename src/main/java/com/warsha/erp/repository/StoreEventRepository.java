package com.warsha.erp.repository;

import com.warsha.erp.entities.StoreEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface StoreEventRepository extends JpaRepository<StoreEvent, Long> {

    /** Gives a visitor's guest events to the customer they just logged in as. */
    @Modifying
    @Query("UPDATE StoreEvent e SET e.customerId = :customerId WHERE e.visitorId = :visitorId AND e.customerId IS NULL")
    int assignCustomer(@Param("visitorId") String visitorId, @Param("customerId") Long customerId);

    /** Deletes up to {@code batchSize} events older than {@code before}; returns how many went. */
    @Transactional
    @Modifying
    @Query(value = "DELETE TOP (:batchSize) FROM StoreEvents WHERE OccurredAt < :before", nativeQuery = true)
    int deleteOlderThan(@Param("before") LocalDateTime before, @Param("batchSize") int batchSize);
}
