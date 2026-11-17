package com.shlyapoff.shop.integration;

import com.shlyapoff.shop.model.NotificationOutbox;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.repository.NotificationOutboxRepository;
import com.shlyapoff.shop.repository.OrderRepository;
import com.shlyapoff.shop.service.NotificationOutboxService;
import com.shlyapoff.shop.service.TelegramNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:outbox-flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "telegram.enabled=false", "telegram.bot-token=test-token", "telegram.admin-chat-id=1",
        "app.notifications.fixed-delay-ms=3600000", "app.orders.reservation-cleanup-ms=3600000"
})
class NotificationOutboxIntegrationTest {
    @Autowired private NotificationOutboxService service;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private TelegramNotificationService telegram;

    @Test
    void sendsOutsideTransactionAndPreventsSecondWorkerFromSendingActiveClaim() {
        NotificationOutbox outbox = pending();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            service.deliver(outbox.getId());
            return null;
        }).when(telegram).notifyAdminAboutNewOrder(any());

        service.deliver(outbox.getId());
        service.deliver(outbox.getId());

        verify(telegram, times(1)).notifyAdminAboutNewOrder(any());
        assertThat(outboxRepository.findById(outbox.getId()).orElseThrow().getSentAt()).isNotNull();
    }

    @Test
    void retriesFailuresAndRecoversExpiredClaimsWithoutAcceptingOldWorkerResult() {
        NotificationOutbox outbox = pending();
        doThrow(new IllegalStateException("Temporary Telegram failure")).when(telegram).notifyAdminAboutNewOrder(any());
        service.deliver(outbox.getId());
        NotificationOutbox failed = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getSentAt()).isNull();
        assertThat(failed.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(failed.getClaimToken()).isNull();

        // Simulate a worker crash followed by expiry of its lease.
        failed.setClaimToken("old-worker");
        failed.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        outboxRepository.saveAndFlush(failed);
        doAnswer(invocation -> {
            int updated = new TransactionTemplate(transactionManager).execute(ignored ->
                    outboxRepository.complete(outbox.getId(), "old-worker", LocalDateTime.now()));
            assertThat(updated).isZero();
            return null;
        }).when(telegram).notifyAdminAboutNewOrder(any());
        service.deliver(outbox.getId());
        assertThat(outboxRepository.findById(outbox.getId()).orElseThrow().getSentAt()).isNotNull();
    }

    private NotificationOutbox pending() {
        Order order = new Order();
        order.setCustomerName("Покупатель");
        order.setDeliveryType("Самовывоз");
        order.setTotalAmount(BigDecimal.TEN);
        order = orderRepository.saveAndFlush(order);
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setOrder(order);
        outbox.setCreatedAt(LocalDateTime.now());
        outbox.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        return outboxRepository.saveAndFlush(outbox);
    }
}
