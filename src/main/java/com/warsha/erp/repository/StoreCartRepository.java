package com.warsha.erp.repository;

import com.warsha.erp.entities.StoreCart;
import com.warsha.erp.entities.StoreCartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface StoreCartRepository extends JpaRepository<StoreCart, String> {

    List<StoreCart> findByCustomerIdAndStatus(Long customerId, StoreCartStatus status);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM StoreCarts WHERE UpdatedAt < :before", nativeQuery = true)
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
