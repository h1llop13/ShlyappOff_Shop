package com.shlyapoff.shop.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationOutboxWorker {

    private final NotificationOutboxService notificationOutboxService;

    @Scheduled(fixedDelayString = "${app.notifications.fixed-delay-ms}")
    public void deliverPendingNotifications() {
        notificationOutboxService.findReadyNotificationIds()
                .forEach(notificationOutboxService::deliver);
    }
}
