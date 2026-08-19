package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.StockSubscription;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface StockSubscriptionRepository extends JpaRepository<StockSubscription, Long> {
    Optional<StockSubscription> findByProductIdAndTelegramUserId(Long productId, Long telegramUserId);

    @Query("""
            SELECT s FROM StockSubscription s JOIN FETCH s.product p
            WHERE s.queuedAt IS NULL AND p.active = true
              AND (p.stockQuantity > 0 OR EXISTS (
                  SELECT v.id FROM ProductVariant v WHERE v.product = p AND v.stockQuantity > 0
              ))
            ORDER BY s.id
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StockSubscription> findAvailablePending(Pageable pageable);
}
