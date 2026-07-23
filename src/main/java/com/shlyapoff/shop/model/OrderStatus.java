package com.shlyapoff.shop.model;

import java.util.Locale;

public enum OrderStatus {
    NEW,
    PROCESSING,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus nextStatus) {
        return switch (this) {
            case NEW -> nextStatus == PROCESSING || nextStatus == COMPLETED || nextStatus == CANCELLED;
            case PROCESSING -> nextStatus == COMPLETED || nextStatus == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public static OrderStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Статус заказа не указан");
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Недопустимый статус заказа: " + value, exception);
        }
    }
}
