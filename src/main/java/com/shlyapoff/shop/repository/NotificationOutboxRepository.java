package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    List<NotificationOutbox> findTop10BySentAtIsNullAndNextAttemptAtLessThanEqualOrderById(LocalDateTime now);
}
