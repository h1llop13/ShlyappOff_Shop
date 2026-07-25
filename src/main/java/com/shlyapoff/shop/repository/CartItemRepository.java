package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Query("""
            SELECT ci FROM CartItem ci
            WHERE ci.cart.id = :cartId
              AND ci.product.id = :productId
              AND ((:variantId IS NULL AND ci.productVariant IS NULL) OR ci.productVariant.id = :variantId)
            """)
    Optional<CartItem> findByCartIdAndProductIdAndVariantId(
            @Param("cartId") Long cartId,
            @Param("productId") Long productId,
            @Param("variantId") Long variantId);
}
