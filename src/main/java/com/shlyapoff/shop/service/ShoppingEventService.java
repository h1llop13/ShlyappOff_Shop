package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.*;
import com.shlyapoff.shop.repository.ShoppingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShoppingEventService {
    private final ShoppingEventRepository shoppingEventRepository;

    public record FunnelData(long productViews, long cartAdds, long checkoutStarts, long orders,
                             long abandonedCarts, double viewToCartPercent, double cartToCheckoutPercent,
                             double checkoutToOrderPercent, List<AbandonmentData> abandonmentReasons) {}
    public record AbandonmentData(AbandonmentReason reason, long total) {}

    @Transactional
    public void recordProductView(String sessionId, Long telegramUserId, Product product) {
        save(sessionId, telegramUserId, product, null, ShoppingEventType.PRODUCT_VIEW, null);
    }

    @Transactional
    public void recordCartAdd(String sessionId, Long telegramUserId, Product product) {
        save(sessionId, telegramUserId, product, null, ShoppingEventType.CART_ADD, null);
    }

    @Transactional
    public void recordCheckoutStarted(String sessionId, Long telegramUserId) {
        save(sessionId, telegramUserId, null, null, ShoppingEventType.CHECKOUT_STARTED, null);
    }

    @Transactional
    public void recordOrderCreated(String sessionId, Long telegramUserId, Order order) {
        save(sessionId, telegramUserId, null, order, ShoppingEventType.ORDER_CREATED, null);
    }

    @Transactional
    public void recordAbandoned(String sessionId, Long telegramUserId, AbandonmentReason reason) {
        save(sessionId, telegramUserId, null, null, ShoppingEventType.CART_ABANDONED,
                reason == null ? AbandonmentReason.OTHER : reason);
    }

    @Transactional(readOnly = true)
    public FunnelData getFunnel(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days));
        long views = count(ShoppingEventType.PRODUCT_VIEW, since);
        long carts = count(ShoppingEventType.CART_ADD, since);
        long checkouts = count(ShoppingEventType.CHECKOUT_STARTED, since);
        long orders = count(ShoppingEventType.ORDER_CREATED, since);
        long abandoned = count(ShoppingEventType.CART_ABANDONED, since);
        List<AbandonmentData> reasons = shoppingEventRepository.countAbandonmentReasons(since).stream()
                .map(row -> new AbandonmentData(row.getReason(), row.getTotal())).toList();
        return new FunnelData(views, carts, checkouts, orders, abandoned,
                percent(carts, views), percent(checkouts, carts), percent(orders, checkouts), reasons);
    }

    @Scheduled(fixedDelayString = "${app.analytics.abandoned-cart-check-ms:3600000}")
    @Transactional
    public void markInactiveCarts() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        shoppingEventRepository.findInactiveCartSessions(cutoff).forEach(sessionId ->
                save(sessionId, null, null, null, ShoppingEventType.CART_ABANDONED, AbandonmentReason.INACTIVITY));
    }

    private long count(ShoppingEventType type, LocalDateTime since) {
        return shoppingEventRepository.countDistinctSessionIdByEventTypeAndCreatedAtAfter(type, since);
    }

    private double percent(long value, long base) {
        return base == 0 ? 0 : Math.round(value * 1000.0 / base) / 10.0;
    }

    private void save(String sessionId, Long telegramUserId, Product product, Order order,
                      ShoppingEventType type, AbandonmentReason reason) {
        ShoppingEvent event = new ShoppingEvent();
        event.setSessionId(sessionId);
        event.setTelegramUserId(telegramUserId);
        event.setProduct(product);
        event.setOrder(order);
        event.setEventType(type);
        event.setAbandonmentReason(reason);
        event.setCreatedAt(LocalDateTime.now());
        shoppingEventRepository.save(event);
    }
}
