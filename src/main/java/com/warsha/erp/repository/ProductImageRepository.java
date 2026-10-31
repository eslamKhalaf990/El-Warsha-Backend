package com.warsha.erp.repository;

import com.warsha.erp.entities.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query("SELECT i FROM ProductImage i WHERE i.product.ProductID = :productId ORDER BY i.sortOrder, i.id")
    List<ProductImage> findByProductId(@Param("productId") Long productId);

    /** Gallery images of many products in one query, for product lists. */
    @Query("SELECT i FROM ProductImage i WHERE i.product.ProductID IN :productIds ORDER BY i.sortOrder, i.id")
    List<ProductImage> findByProductIds(@Param("productIds") Collection<Long> productIds);
}
