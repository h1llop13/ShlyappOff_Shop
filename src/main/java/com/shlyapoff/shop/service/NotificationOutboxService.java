package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.NotificationOutbox;
import com.shlyapoff.shop.model.NotificationType;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.PromoCode;
import com.shlyapoff.shop.repository.NotificationOutboxRepository;
import com.shlyapoff.shop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxService {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final OrderRepository orderRepository;
    private final TelegramNotificationService telegramNotificationService;

    @Transactional
    public void enqueueNewOrderNotification(Order order) {
        notificationOutboxRepository.save(newOutbox(order, NotificationType.ADMIN_NEW_ORDER));
    }

    @Transactional
    public void enqueueCustomerStatusNotification(Order order, OrderStatus previousStatus,
                                                  OrderStatus targetStatus, String cancellationReason) {
        if (order.getTelegramUserId() == null) {
            return;
        }
        NotificationOutbox outbox = newOutbox(order, NotificationType.CUSTOMER_STATUS_CHANGED);
        outbox.setPreviousStatus(previousStatus);
        outbox.setTargetStatus(targetStatus);
        outbox.setCancellationReason(cancellationReason);
        notificationOutboxRepository.save(outbox);
    }

    @Transactional
    public void enqueueStockAvailable(Product product, Long telegramUserId) {
        NotificationOutbox outbox = newOutbox(null, NotificationType.STOCK_AVAILABLE);
        outbox.setProduct(product);
        outbox.setTelegramUserId(telegramUserId);
        notificationOutboxRepository.save(outbox);
    }

    @Transactional
    public void enqueuePersonalPromoCode(PromoCode promoCode, Long telegramUserId) {
        NotificationOutbox outbox = newOutbox(null, NotificationType.PERSONAL_PROMO_CODE);
        outbox.setPromoCode(promoCode);
        outbox.setTelegramUserId(telegramUserId);
        notificationOutboxRepository.save(outbox);
    }

    @Transactional(readOnly = true)
    public List<Long> findReadyNotificationIds() {
        return notificationOutboxRepository.findTop10BySentAtIsNullAndNextAttemptAtLessThanEqualOrderById(LocalDateTime.now())
                .stream()
                .map(NotificationOutbox::getId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long outboxId) {
        NotificationOutbox outbox = notificationOutboxRepository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getSentAt() != null || outbox.getNextAttemptAt().isAfter(LocalDateTime.now())) {
            return;
        }

        try {
            switch (outbox.getNotificationType()) {
                case ADMIN_NEW_ORDER -> telegramNotificationService.notifyAdminAboutNewOrder(loadOrder(outbox));
                case CUSTOMER_STATUS_CHANGED -> telegramNotificationService.notifyCustomerAboutStatusChange(
                        loadOrder(outbox), outbox.getPreviousStatus(), outbox.getTargetStatus(), outbox.getCancellationReason());
                case STOCK_AVAILABLE -> telegramNotificationService.notifyStockAvailable(
                        outbox.getTelegramUserId(), outbox.getProduct());
                case PERSONAL_PROMO_CODE -> telegramNotificationService.notifyPersonalPromoCode(
                        outbox.getTelegramUserId(), outbox.getPromoCode());
            }
            outbox.setSentAt(LocalDateTime.now());
            outbox.setLastError(null);
        } catch (RuntimeException exception) {
            int attempts = outbox.getAttempts() + 1;
            outbox.setAttempts(attempts);
            outbox.setLastError(exception.getMessage());
            long delaySeconds = Math.min(300, 5L * (1L << Math.min(attempts, 6)));
            outbox.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
            log.warn("Не удалось отправить уведомление по outbox {}. Повтор через {} сек.", outboxId, delaySeconds, exception);
        }
    }

    private NotificationOutbox newOutbox(Order order, NotificationType notificationType) {
        LocalDateTime now = LocalDateTime.now();
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setOrder(order);
        outbox.setNotificationType(notificationType);
        outbox.setCreatedAt(now);
        outbox.setNextAttemptAt(now);
        return outbox;
    }

    private Order loadOrder(NotificationOutbox outbox) {
        if (outbox.getOrder() == null) {
            throw new IllegalStateException("В уведомлении не указан заказ");
        }
        return orderRepository.findByIdWithItems(outbox.getOrder().getId())
                .orElseThrow(() -> new IllegalStateException("Заказ для уведомления не найден"));
    }
}
