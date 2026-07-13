package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.LoyaltyTier;
import com.shlyapoff.shop.repository.LoyaltyTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LoyaltyTierService {

    private final LoyaltyTierRepository loyaltyTierRepository;

    public List<LoyaltyTier> findAll() {
        return loyaltyTierRepository.findAllByOrderByMinAmountAsc();
    }

    public Optional<LoyaltyTier> findById(Long id) {
        return loyaltyTierRepository.findById(id);
    }

    public LoyaltyTier save(LoyaltyTier tier) {
        return loyaltyTierRepository.save(tier);
    }

    public void deleteById(Long id) {
        loyaltyTierRepository.deleteById(id);
    }

    /**
     * Скидка (в процентах), положенная клиенту с суммарной суммой заказов totalSpent.
     * Если клиент не достиг ни одного порога — скидка 0.
     */
    public int resolveDiscountPercent(BigDecimal totalSpent) {
        if (totalSpent == null) {
            return 0;
        }
        return loyaltyTierRepository.findTopByMinAmountLessThanEqualOrderByMinAmountDesc(totalSpent)
                .map(LoyaltyTier::getDiscountPercent)
                .orElse(0);
    }

    /**
     * Следующий недостигнутый порог — используется, чтобы показать клиенту в профиле,
     * сколько ему осталось потратить до следующей скидки.
     */
    public Optional<LoyaltyTier> findNextTier(BigDecimal totalSpent) {
        BigDecimal amount = totalSpent == null ? BigDecimal.ZERO : totalSpent;
        return loyaltyTierRepository.findTopByMinAmountGreaterThanOrderByMinAmountAsc(amount);
    }
}
