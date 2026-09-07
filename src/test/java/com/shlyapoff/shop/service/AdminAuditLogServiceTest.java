package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.AdminAuditAction;
import com.shlyapoff.shop.model.AdminAuditLog;
import com.shlyapoff.shop.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    @Mock private AdminAuditLogRepository repository;
    @InjectMocks private AdminAuditLogService auditLogService;

    @Test
    void storesActorAndNormalizedMoneyValues() {
        auditLogService.recordChange(
                "admin-one", AdminAuditAction.PRODUCT_PRICE_CHANGED,
                "PRODUCT", 4L, "price", new BigDecimal("10"), new BigDecimal("12.5"));

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        AdminAuditLog entry = captor.getValue();
        assertThat(entry.getActorUsername()).isEqualTo("admin-one");
        assertThat(entry.getOldValue()).isEqualTo("10.00");
        assertThat(entry.getNewValue()).isEqualTo("12.50");
    }

    @Test
    void doesNotStoreAnUnchangedMoneyValueWithAnotherScale() {
        auditLogService.recordChange(
                "admin-one", AdminAuditAction.PRODUCT_PRICE_CHANGED,
                "PRODUCT", 4L, "price", new BigDecimal("10.0"), new BigDecimal("10.00"));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
