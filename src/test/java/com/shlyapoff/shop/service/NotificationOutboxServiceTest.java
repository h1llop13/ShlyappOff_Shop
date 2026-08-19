package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.NotificationOutbox;
import com.shlyapoff.shop.model.NotificationType;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.repository.NotificationOutboxRepository;
import com.shlyapoff.shop.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTest {

    @Mock private NotificationOutboxRepository notificationOutboxRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TelegramNotificationService telegramNotificationService;
    @InjectMocks private NotificationOutboxService service;

    @Test
    void storesTransitionSnapshotAndDeliversItToCustomer() {
        Order order = new Order();
        order.setId(42L);
        order.setTelegramUserId(100500L);

        service.enqueueCustomerStatusNotification(
                order, OrderStatus.PAID, OrderStatus.CANCELLED, "Ошибка комплектации");

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(notificationOutboxRepository).save(captor.capture());
        NotificationOutbox outbox = captor.getValue();
        assertThat(outbox.getNotificationType()).isEqualTo(NotificationType.CUSTOMER_STATUS_CHANGED);
        assertThat(outbox.getPreviousStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(outbox.getTargetStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outbox.getCancellationReason()).isEqualTo("Ошибка комплектации");

        outbox.setId(7L);
        when(notificationOutboxRepository.findById(7L)).thenReturn(Optional.of(outbox));
        when(orderRepository.findByIdWithItems(42L)).thenReturn(Optional.of(order));

        service.deliver(7L);

        verify(telegramNotificationService).notifyCustomerAboutStatusChange(
                order, OrderStatus.PAID, OrderStatus.CANCELLED, "Ошибка комплектации");
        assertThat(outbox.getSentAt()).isNotNull();
    }
}
