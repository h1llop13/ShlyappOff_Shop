package com.shlyapoff.shop.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Порог программы лояльности: при достижении суммарной суммы всех заказов
 * клиента значения {@code minAmount} и выше — клиенту присваивается скидка
 * {@code discountPercent} на все последующие заказы.
 * Управляется администратором через /admin/loyalty.
 */
@Entity
@Table(name = "loyalty_tiers")
@Data
public class LoyaltyTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_amount", nullable = false, unique = true)
    private BigDecimal minAmount;

    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent;
}
