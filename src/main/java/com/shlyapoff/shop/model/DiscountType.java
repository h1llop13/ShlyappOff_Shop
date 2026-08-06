package com.shlyapoff.shop.model;

public enum DiscountType {
    PERCENTAGE("Процент"),
    FIXED_AMOUNT("Фиксированная сумма");

    private final String displayName;

    DiscountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
