package com.warsha.erp.repository;

import com.warsha.erp.dtos.OrderCountByGovernorateDto;
import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("SELECT new com.warsha.erp.dtos.OrderCountByGovernorateDto(c.governorate, COUNT(o)) " +
            "FROM Order o JOIN o.customer c " +
            "GROUP BY c.governorate ORDER BY COUNT(c) DESC")
    List<OrderCountByGovernorateDto> countOrdersByGovernorate();

    /** Orders in a date range (optionally one status) with customer, items and products in one query. */
    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.customer
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            WHERE o.orderDate BETWEEN :start AND :end
              AND (:status IS NULL OR o.status = :status)
            ORDER BY o.id DESC
            """)
    List<Order> findForList(@Param("start") LocalDate start, @Param("end") LocalDate end,
                            @Param("status") OrderStatus status);

    /** All orders of one customer with items and products in one query. */
    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.customer c
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            WHERE c.id = :customerId
            ORDER BY o.id DESC
            """)
    List<Order> findForCustomer(@Param("customerId") Long customerId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId")
    long countByCustomerId(@Param("customerId") Long customerId);
}