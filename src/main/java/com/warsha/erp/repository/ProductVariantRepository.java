package com.warsha.erp.repository;

import com.warsha.erp.entities.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @Query("SELECT v FROM ProductVariant v WHERE v.product.ProductID = :productId AND v.deleted = false ORDER BY v.id")
    List<ProductVariant> findActiveByProductId(@Param("productId") Long productId);

    /** Active variants of many products in one query, for product lists. */
    @Query("SELECT v FROM ProductVariant v WHERE v.product.ProductID IN :productIds AND v.deleted = false ORDER BY v.id")
    List<ProductVariant> findActiveByProductIds(@Param("productIds") Collection<Long> productIds);
}
