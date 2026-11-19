package com.shlyapoff.shop.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxWorker {

    private final NotificationOutboxService notificationOutboxService;

    @Scheduled(fixedDelayString = "${app.notifications.fixed-delay-ms}")
    public void deliverPendingNotifications() {
        for (Long id : notificationOutboxService.findReadyNotificationIds()) {
            try {
                notificationOutboxService.deliver(id);
            } catch (RuntimeException exception) {
                log.warn("Ошибка обработки уведомления {}", id, exception);
            }
        }
    }
}
