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
            Integer discountPercent,
            BigDecimal totalAmount,
            List<OrderItemView> items
    ) {}

    public record LoyaltyProgress(
            BigDecimal totalSpent,
            Integer currentDiscountPercent,
            Integer nextDiscountPercent,
            BigDecimal amountLeftToNextDiscount
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
