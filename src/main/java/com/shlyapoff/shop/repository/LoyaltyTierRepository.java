package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.LoyaltyTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LoyaltyTierRepository extends JpaRepository<LoyaltyTier, Long> {

    List<LoyaltyTier> findAllByOrderByMinAmountAsc();

    /**
     * Находит ближайший (максимальный) порог, сумма которого не превышает totalSpent —
     * то есть скидку, которая уже "выслужена" клиентом.
     */
    Optional<LoyaltyTier> findTopByMinAmountLessThanEqualOrderByMinAmountDesc(BigDecimal totalSpent);

    /**
     * Следующий (ещё не достигнутый) порог — чтобы показать клиенту,
     * сколько ему осталось потратить до следующей скидки.
     */
    Optional<LoyaltyTier> findTopByMinAmountGreaterThanOrderByMinAmountAsc(BigDecimal totalSpent);
}
