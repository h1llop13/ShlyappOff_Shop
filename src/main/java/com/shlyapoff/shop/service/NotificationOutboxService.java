package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.NotificationOutbox;
import com.shlyapoff.shop.model.Order;
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
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setOrder(order);
        outbox.setCreatedAt(LocalDateTime.now());
        outbox.setNextAttemptAt(LocalDateTime.now());
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
            Order order = orderRepository.findByIdWithItems(outbox.getOrder().getId())
                    .orElseThrow(() -> new IllegalStateException("Заказ для уведомления не найден"));
            telegramNotificationService.notifyAdminAboutNewOrder(order);
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
}
