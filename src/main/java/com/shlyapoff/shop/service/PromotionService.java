package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Promotion;
import com.shlyapoff.shop.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PromotionService {
    private final PromotionRepository promotionRepository;

    public List<Promotion> findAll() {
        return promotionRepository.findAllByOrderByDisplayPriorityDescCreatedAtDesc();
    }

    public Optional<Promotion> findById(Long id) {
        return promotionRepository.findById(id);
    }

    public Promotion save(Promotion promotion) {
        return promotionRepository.save(promotion);
    }

    public void deleteById(Long id) {
        promotionRepository.deleteById(id);
    }

    public boolean hasValidSchedule(Promotion promotion) {
        return promotion.getStartsAt() == null || promotion.getEndsAt() == null
                || promotion.getEndsAt().isAfter(promotion.getStartsAt());
    }
}
