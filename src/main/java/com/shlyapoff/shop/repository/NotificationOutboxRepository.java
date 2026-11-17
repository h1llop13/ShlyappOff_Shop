package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.NotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    @Query("SELECT n.id FROM NotificationOutbox n WHERE n.sentAt IS NULL AND n.nextAttemptAt <= :now " +
            "ORDER BY n.nextAttemptAt, n.id")
    List<Long> findReadyIds(LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("UPDATE NotificationOutbox n SET n.claimToken = :token, n.nextAttemptAt = :leaseUntil " +
            "WHERE n.id = :id AND n.sentAt IS NULL AND n.nextAttemptAt <= :now")
    int claim(Long id, String token, LocalDateTime now, LocalDateTime leaseUntil);

    @Modifying
    @Query("UPDATE NotificationOutbox n SET n.sentAt = :sentAt, n.lastError = NULL, n.claimToken = NULL " +
            "WHERE n.id = :id AND n.claimToken = :token AND n.sentAt IS NULL")
    int complete(Long id, String token, LocalDateTime sentAt);

    @Modifying
    @Query("UPDATE NotificationOutbox n SET n.attempts = :attempts, n.nextAttemptAt = :nextAttempt, " +
            "n.lastError = :error, n.claimToken = NULL " +
            "WHERE n.id = :id AND n.claimToken = :token AND n.sentAt IS NULL")
    int retry(Long id, String token, int attempts, LocalDateTime nextAttempt, String error);
}
