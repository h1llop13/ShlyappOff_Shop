package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.dto.ProductCard;
import com.shlyapoff.shop.model.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true
            ORDER BY p.createdAt DESC
            """)
    List<ProductCard> findLatestActiveCards(Pageable pageable);

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true
            ORDER BY p.createdAt DESC
            """)
    Page<ProductCard> findAllActiveCards(Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Product> findByActiveTrue(Pageable pageable);

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true AND lower(p.name) LIKE lower(concat('%', :name, '%'))
            ORDER BY p.createdAt DESC
            """)
    Page<ProductCard> findCardsByName(@Param("name") String name, Pageable pageable);

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true AND p.category.id = :categoryId
            ORDER BY p.createdAt DESC
            """)
    Page<ProductCard> findCardsByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true AND p.brand.id = :brandId
            ORDER BY p.createdAt DESC
            """)
    Page<ProductCard> findCardsByBrand(@Param("brandId") Long brandId, Pageable pageable);

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true AND lower(p.name) LIKE lower(concat('%', :name, '%')) AND p.category.id = :categoryId
            ORDER BY p.createdAt DESC
            """)
    Page<ProductCard> findCardsByNameAndCategory(@Param("name") String name, @Param("categoryId") Long categoryId, Pageable pageable);

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true AND lower(p.name) LIKE lower(concat('%', :name, '%')) AND p.brand.id = :brandId
            ORDER BY p.createdAt DESC
            """)
    Page<ProductCard> findCardsByNameAndBrand(@Param("name") String name, @Param("brandId") Long brandId, Pageable pageable);

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true AND p.category.id = :categoryId AND p.brand.id = :brandId
            ORDER BY p.createdAt DESC
            """)
    Page<ProductCard> findCardsByCategoryAndBrand(@Param("categoryId") Long categoryId, @Param("brandId") Long brandId, Pageable pageable);

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true AND lower(p.name) LIKE lower(concat('%', :name, '%'))
                AND p.category.id = :categoryId AND p.brand.id = :brandId
            ORDER BY p.createdAt DESC
            """)
    Page<ProductCard> findCardsByNameAndCategoryAndBrand(@Param("name") String name,
                                                          @Param("categoryId") Long categoryId,
                                                          @Param("brandId") Long brandId,
                                                          Pageable pageable);

    @Query("""
            SELECT new com.shlyapoff.shop.dto.ProductCard(p.id, p.name, p.description, p.price,
                p.stockQuantity, p.imageUrl, p.imageThumbnailUrl, c.variantType)
            FROM Product p LEFT JOIN p.category c
            WHERE p.active = true
              AND (:name IS NULL OR lower(p.name) LIKE lower(concat('%', :name, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:brandId IS NULL OR p.brand.id = :brandId)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:onlyInStock = false OR p.stockQuantity > 0 OR EXISTS (
                    SELECT v.id FROM ProductVariant v WHERE v.product = p AND v.stockQuantity > 0
              ))
            """)
    Page<ProductCard> findCardsWithFilters(@Param("name") String name,
                                           @Param("categoryId") Long categoryId,
                                           @Param("brandId") Long brandId,
                                           @Param("minPrice") java.math.BigDecimal minPrice,
                                           @Param("maxPrice") java.math.BigDecimal maxPrice,
                                           @Param("onlyInStock") boolean onlyInStock,
                                           Pageable pageable);
    List<Product> findByCategory_Id(Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.variants LEFT JOIN FETCH p.category LEFT JOIN FETCH p.brand WHERE p.id = :id")
    Optional<Product> findByIdWithVariants(@Param("id") Long id);
}
