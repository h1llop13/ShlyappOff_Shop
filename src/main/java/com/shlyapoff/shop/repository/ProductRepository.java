package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrue();
    List<Product> findByCategory_Id(Long categoryId);

    @Query(value = "SELECT * FROM products p WHERE p.is_active = true " +
            "AND (:name IS NULL OR LOWER(p.name) LIKE LOWER('%' || CAST(:name AS VARCHAR) || '%')) " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:brandId IS NULL OR p.brand_id = :brandId)",
            nativeQuery = true)
    Page<Product> findWithFilters(
            @Param("name") String name,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            Pageable pageable
    );

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.id = :id")
    Optional<Product> findByIdWithVariants(@Param("id") Long id);
}
