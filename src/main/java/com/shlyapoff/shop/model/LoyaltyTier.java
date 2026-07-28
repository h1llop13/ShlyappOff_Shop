package com.shlyapoff.shop.model;

import jakarta.persistence.*;
import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Порог программы лояльности: при достижении суммарной суммы всех заказов
 * клиента значения {@code minAmount} и выше — клиенту присваивается скидка
 * {@code bonusPercent} от подтверждённого заказа в виде бонусов.
 * Управляется администратором через /admin/loyalty.
 */
@Entity
@Table(name = "loyalty_tiers")
@Data
public class LoyaltyTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @PositiveOrZero
    @Column(name = "min_amount", nullable = false, unique = true)
    private BigDecimal minAmount;

    @Min(0)
    @Max(100)
    @Column(name = "bonus_percent", nullable = false)
    private Integer bonusPercent;
}
