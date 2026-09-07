package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.AdminAuditAction;
import com.shlyapoff.shop.model.AdminAuditLog;
import com.shlyapoff.shop.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;

    public void recordChange(String actorUsername, AdminAuditAction action, String entityType,
                             Long entityId, String fieldName, Object oldValue, Object newValue) {
        if (sameValue(oldValue, newValue)) {
            return;
        }
        repository.save(new AdminAuditLog(
                normalizeActor(actorUsername),
                action,
                entityType,
                entityId,
                fieldName,
                formatValue(oldValue),
                formatValue(newValue)
        ));
    }

    public Page<AdminAuditLog> findRecent(int page) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), 50));
    }

    private boolean sameValue(Object oldValue, Object newValue) {
        if (oldValue instanceof BigDecimal oldMoney && newValue instanceof BigDecimal newMoney) {
            return oldMoney.compareTo(newMoney) == 0;
        }
        return Objects.equals(oldValue, newValue);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal money) {
            return money.setScale(2, com.shlyapoff.shop.money.Money.ROUNDING_MODE).toPlainString();
        }
        return value.toString();
    }

    private String normalizeActor(String actorUsername) {
        return actorUsername == null || actorUsername.isBlank() ? "system" : actorUsername;
    }
}
