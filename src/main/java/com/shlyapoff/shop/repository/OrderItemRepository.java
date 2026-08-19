package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    @Query(value = """
            SELECT together.product_id
            FROM order_items selected
            JOIN orders o ON o.id = selected.order_id
            JOIN order_items together ON together.order_id = selected.order_id
            JOIN products p ON p.id = together.product_id
            WHERE selected.product_id = :productId
              AND together.product_id <> :productId
              AND o.status <> 'CANCELLED'
              AND p.is_active = TRUE
            GROUP BY together.product_id
            ORDER BY COUNT(DISTINCT selected.order_id) DESC, together.product_id
            """, nativeQuery = true)
    List<Long> findBoughtTogetherProductIds(@Param("productId") Long productId, Pageable pageable);
}
