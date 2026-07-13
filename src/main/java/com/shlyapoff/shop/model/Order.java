package com.shlyapoff.shop.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "delivery_type", nullable = false)
    private String deliveryType;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /**
     * Сумма товаров ДО применения скидки по программе лояльности.
     */
    @Column(name = "subtotal_amount")
    private BigDecimal subtotalAmount;

    /**
     * Скидка (в процентах), применённая к этому заказу.
     */
    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent = 0;

    /**
     * Итоговая сумма к оплате (subtotalAmount за вычетом скидки).
     */
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "status", nullable = false)
    private String status = "NEW";

    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "telegram_username")
    private String telegramUsername;

    /**
     * Профиль клиента (заводится автоматически при заказе из Telegram Mini App).
     * Может быть null для заказов, оформленных вне Telegram (без telegramUserId).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();


    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
