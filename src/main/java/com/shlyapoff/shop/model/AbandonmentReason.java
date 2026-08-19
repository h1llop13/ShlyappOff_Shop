package com.shlyapoff.shop.model;

public enum AbandonmentReason {
    DELIVERY_COST("Дорогая доставка"),
    TOTAL_PRICE("Высокая итоговая цена"),
    PAYMENT_METHOD("Не подошёл способ оплаты"),
    NEED_MORE_TIME("Нужно время подумать"),
    TECHNICAL_PROBLEM("Техническая проблема"),
    REMOVED_LAST_ITEM("Удалён последний товар"),
    CART_CLEARED("Корзина очищена"),
    INACTIVITY("Нет активности более суток"),
    OTHER("Другая причина");

    private final String displayName;

    AbandonmentReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
