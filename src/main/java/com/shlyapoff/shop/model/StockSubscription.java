package com.shlyapoff.shop.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_subscriptions", uniqueConstraints =
        @UniqueConstraint(name = "uq_stock_subscriptions_product_user", columnNames = {"product_id", "telegram_user_id"}))
@Data
public class StockSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;
}
