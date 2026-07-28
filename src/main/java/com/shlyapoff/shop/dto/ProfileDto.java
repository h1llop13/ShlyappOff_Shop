package com.shlyapoff.shop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Данные профиля клиента для страницы /profile в Telegram Mini App.
 */
public class ProfileDto {

    public record OrderItemView(
            String productName,
            Integer quantity,
            BigDecimal priceAtMoment
    ) {}

    public record OrderView(
            Long id,
            LocalDateTime createdAt,
            String status,
            String deliveryType,
            BigDecimal subtotalAmount,
            BigDecimal bonusesSpent,
            BigDecimal bonusesEarned,
            BigDecimal totalAmount,
            List<OrderItemView> items
    ) {}

    public record LoyaltyProgress(
            BigDecimal bonusBalance,
            BigDecimal totalSpent,
            Integer currentBonusPercent,
            Integer nextBonusPercent,
            BigDecimal amountLeftToNextBonus
    ) {}

    public record ProfileResponse(
            Long telegramUserId,
            String telegramUsername,
            String firstName,
            String lastName,
            LoyaltyProgress loyalty,
            List<OrderView> orders
    ) {}
}
