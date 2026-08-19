package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.StockSubscription;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.StockSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StockSubscriptionService {
    private final StockSubscriptionRepository stockSubscriptionRepository;
    private final ProductRepository productRepository;
    private final NotificationOutboxService notificationOutboxService;

    @Transactional
    public boolean subscribe(Long productId, Long telegramUserId) {
        Product product = productRepository.findByIdWithVariants(productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        if (product.getAvailableStockQuantity() > 0) {
            throw new IllegalStateException("Товар уже есть в наличии");
        }
        if (stockSubscriptionRepository.findByProductIdAndTelegramUserId(productId, telegramUserId).isPresent()) {
            return false;
        }
        StockSubscription subscription = new StockSubscription();
        subscription.setProduct(product);
        subscription.setTelegramUserId(telegramUserId);
        subscription.setCreatedAt(LocalDateTime.now());
        stockSubscriptionRepository.save(subscription);
        return true;
    }

    @Scheduled(fixedDelayString = "${app.notifications.stock-check-ms:60000}")
    @Transactional
    public void enqueueAvailableProducts() {
        stockSubscriptionRepository.findAvailablePending(PageRequest.of(0, 100)).forEach(subscription -> {
            notificationOutboxService.enqueueStockAvailable(
                    subscription.getProduct(), subscription.getTelegramUserId());
            subscription.setQueuedAt(LocalDateTime.now());
        });
    }
}
