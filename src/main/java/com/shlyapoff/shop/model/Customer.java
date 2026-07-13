package com.shlyapoff.shop.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Профиль клиента Telegram Mini App.
 * Заводится автоматически при первом оформлении заказа из мини-приложения
 * (по telegramUserId) и используется для хранения истории заказов
 * и расчёта скидки по программе лояльности.
 */
@Entity
@Table(name = "customers")
@Data
@EntityListeners(AuditingEntityListener.class)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone")
    private String phone;

    /**
     * Суммарная стоимость всех оформленных заказов (до применения скидки).
     * Именно от этой суммы считается порог программы лояльности.
     */
    @Column(name = "total_spent", nullable = false)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    /**
     * Текущая скидка (в процентах), которая будет применена к СЛЕДУЮЩЕМУ заказу.
     * Пересчитывается после каждого оформленного заказа на основе totalSpent
     * и таблицы порогов loyalty_tiers.
     */
    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
