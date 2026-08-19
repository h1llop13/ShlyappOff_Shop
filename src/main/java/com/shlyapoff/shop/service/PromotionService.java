package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Promotion;
import com.shlyapoff.shop.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.shlyapoff.shop.model.PublicationStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<Promotion> findActive() {
        return promotionRepository.findActive(LocalDateTime.now(), PublicationStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public Optional<Promotion> findActiveById(Long id) {
        LocalDateTime now = LocalDateTime.now();
        return promotionRepository.findByIdWithPromoCode(id)
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .filter(p -> p.getPublicationStatus() == PublicationStatus.PUBLISHED)
                .filter(p -> p.getStartsAt() == null || !now.isBefore(p.getStartsAt()))
                .filter(p -> p.getEndsAt() == null || now.isBefore(p.getEndsAt()));
    }

    public Promotion save(Promotion promotion) {
        applyPublicationState(promotion);
        return promotionRepository.save(promotion);
    }

    public void deleteById(Long id) {
        promotionRepository.deleteById(id);
    }

    public boolean hasValidSchedule(Promotion promotion) {
        return promotion.getStartsAt() == null || promotion.getEndsAt() == null
                || promotion.getEndsAt().isAfter(promotion.getStartsAt());
    }

    @Scheduled(fixedDelayString = "${app.publication.check-ms:30000}")
    @Transactional
    public void publishScheduledPromotions() {
        promotionRepository.publishScheduled(LocalDateTime.now(), PublicationStatus.SCHEDULED, PublicationStatus.PUBLISHED);
    }

    public void applyPublicationState(Promotion promotion) {
        PublicationStatus status = promotion.getPublicationStatus() == null
                ? PublicationStatus.DRAFT : promotion.getPublicationStatus();
        promotion.setPublicationStatus(status);
        if (status == PublicationStatus.SCHEDULED && promotion.getPublishAt() == null) {
            throw new IllegalArgumentException("Для отложенной публикации укажите дату и время");
        }
        if (status == PublicationStatus.SCHEDULED && !promotion.getPublishAt().isAfter(LocalDateTime.now())) {
            status = PublicationStatus.PUBLISHED;
            promotion.setPublicationStatus(status);
        }
        promotion.setActive(status == PublicationStatus.PUBLISHED);
        if (status != PublicationStatus.SCHEDULED) promotion.setPublishAt(null);
    }
}
