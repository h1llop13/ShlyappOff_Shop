package com.shlyapoff.shop.model;

import java.util.Locale;

public enum OrderStatus {
    NEW("Новый"),
    CONFIRMED("Подтверждён"),
    PAYMENT_PENDING("Ожидает оплаты"),
    PAID("Оплачен"),
    ASSEMBLING("Собирается"),
    READY("Готов к выдаче"),
    SHIPPED("Передан в доставку"),
    COMPLETED("Выполнен"),
    CANCELLED("Отменён");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean canTransitionTo(OrderStatus nextStatus) {
        return switch (this) {
            case NEW -> nextStatus == CONFIRMED || nextStatus == CANCELLED;
            case CONFIRMED -> nextStatus == PAYMENT_PENDING || nextStatus == CANCELLED;
            case PAYMENT_PENDING -> nextStatus == PAID || nextStatus == CANCELLED;
            case PAID -> nextStatus == ASSEMBLING || nextStatus == CANCELLED;
            case ASSEMBLING -> nextStatus == READY || nextStatus == SHIPPED || nextStatus == CANCELLED;
            case READY, SHIPPED -> nextStatus == COMPLETED || nextStatus == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
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
