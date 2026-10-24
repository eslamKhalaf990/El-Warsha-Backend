package com.warsha.erp.repository;

import com.warsha.erp.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderIdIn(Collection<Long> orderIds);
}
