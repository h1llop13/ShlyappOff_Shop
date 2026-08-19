package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.AbandonmentReason;
import com.shlyapoff.shop.model.ShoppingEvent;
import com.shlyapoff.shop.model.ShoppingEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShoppingEventRepository extends JpaRepository<ShoppingEvent, Long> {
    @Query(value = """
            SELECT product_id
            FROM shopping_events
            WHERE event_type = 'PRODUCT_VIEW' AND product_id IS NOT NULL
              AND (session_id = :sessionId
                   OR (:telegramUserId IS NOT NULL AND telegram_user_id = :telegramUserId))
            GROUP BY product_id
            ORDER BY MAX(created_at) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findRecentProductIds(@Param("sessionId") String sessionId,
                                    @Param("telegramUserId") Long telegramUserId,
                                    @Param("limit") int limit);

    long countDistinctSessionIdByEventTypeAndCreatedAtAfter(ShoppingEventType eventType, LocalDateTime since);

    @Query("""
            SELECT e.abandonmentReason AS reason, COUNT(DISTINCT e.sessionId) AS total
            FROM ShoppingEvent e
            WHERE e.eventType = com.shlyapoff.shop.model.ShoppingEventType.CART_ABANDONED
              AND e.createdAt >= :since
            GROUP BY e.abandonmentReason
            ORDER BY COUNT(DISTINCT e.sessionId) DESC
            """)
    List<AbandonmentCount> countAbandonmentReasons(@Param("since") LocalDateTime since);

    @Query(value = """
            SELECT DISTINCT cart.session_id
            FROM shopping_events cart
            WHERE cart.event_type = 'CART_ADD' AND cart.created_at <= :cutoff
              AND NOT EXISTS (
                  SELECT 1 FROM shopping_events later
                  WHERE later.session_id = cart.session_id
                    AND later.created_at > cart.created_at
                    AND later.event_type IN ('CART_ADD', 'ORDER_CREATED', 'CART_ABANDONED')
              )
            """, nativeQuery = true)
    List<String> findInactiveCartSessions(@Param("cutoff") LocalDateTime cutoff);

    interface AbandonmentCount {
        AbandonmentReason getReason();
        long getTotal();
    }
}
