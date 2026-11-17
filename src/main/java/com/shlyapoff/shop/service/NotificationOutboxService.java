package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.NotificationOutbox;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.repository.NotificationOutboxRepository;
import com.shlyapoff.shop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxService {
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final OrderRepository orderRepository;
    private final TelegramNotificationService telegramNotificationService;
    private final PlatformTransactionManager transactionManager;

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
        return notificationOutboxRepository.findReadyIds(LocalDateTime.now(), PageRequest.of(0, 10));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deliver(Long outboxId) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String token = UUID.randomUUID().toString();
        Delivery delivery = transaction.execute(ignored -> {
            LocalDateTime now = LocalDateTime.now();
            // A crashed worker's claim becomes available again after five minutes.
            if (notificationOutboxRepository.claim(outboxId, token, now, now.plusMinutes(5)) == 0) {
                return null;
            }
            NotificationOutbox outbox = notificationOutboxRepository.findById(outboxId).orElseThrow();
            Order order = orderRepository.findByIdWithItems(outbox.getOrder().getId())
                    .orElseThrow(() -> new IllegalStateException("Заказ для уведомления не найден"));
            return new Delivery(order, outbox.getAttempts());
        });
        if (delivery == null) return;

        try {
            telegramNotificationService.notifyAdminAboutNewOrder(delivery.order());
        } catch (RuntimeException exception) {
            int attempts = delivery.attempts() + 1;
            long delaySeconds = Math.min(300, 5L * (1L << Math.min(attempts, 6)));
            transaction.executeWithoutResult(ignored -> notificationOutboxRepository.retry(
                    outboxId, token, attempts, LocalDateTime.now().plusSeconds(delaySeconds), exception.getMessage()));
            log.warn("Не удалось отправить уведомление {}. Повтор через {} сек.", outboxId, delaySeconds, exception);
            return;
        }
        transaction.executeWithoutResult(ignored ->
                notificationOutboxRepository.complete(outboxId, token, LocalDateTime.now()));
    }

    private record Delivery(Order order, int attempts) {}
}
