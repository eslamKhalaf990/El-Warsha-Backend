package com.warsha.erp.repository;

import com.warsha.erp.entities.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("SELECT p FROM Product p WHERE p.deleted IS NULL OR p.deleted <> 'true' ORDER BY p.Sold DESC")
    List<Product> findAllNotDeleted();

    @Query("SELECT p FROM Product p WHERE p.Category.CategoryId = :categoryId AND (p.deleted IS NULL OR p.deleted <> 'true') ORDER BY p.ProductID DESC")
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);

    /** [categoryId, count] for every category that has products that are not deleted. */
    @Query("SELECT p.Category.CategoryId, COUNT(p) FROM Product p WHERE p.deleted IS NULL OR p.deleted <> 'true' GROUP BY p.Category.CategoryId")
    List<Object[]> countActiveByCategory();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.Category.CategoryId = :categoryId AND (p.deleted IS NULL OR p.deleted <> 'true')")
    long countActiveByCategoryId(@Param("categoryId") Long categoryId);

    /** Includes deleted products, which still reference the category in the database. */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.Category.CategoryId = :categoryId")
    long countAllByCategoryId(@Param("categoryId") Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.ProductID IN :ids ORDER BY p.ProductID")
    List<Product> findAllByIdWithLock(@Param("ids") List<Long> ids);

}
