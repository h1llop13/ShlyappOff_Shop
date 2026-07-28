package com.shlyapoff.shop.model;

public enum PromotionKind {
    PRODUCT_SELECTION("Подборка товаров"),
    BONUS_EVENT("Повышенные бонусы");

    private final String displayName;

    PromotionKind(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
