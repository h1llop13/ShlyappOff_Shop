package com.shlyapoff.shop.model;

/**
 * Describes what a product variant means for products in a category.
 */
public enum VariantType {
    NONE("Без вариантов", "вариант", "Варианты"),
    FLAVOR("Вкусы", "вкус", "Вкусы"),
    COLOR("Расцветки", "расцветку", "Расцветки"),
    COMPATIBILITY("Совместимость", "совместимость", "Совместимость");

    private final String displayName;
    private final String singularAccusative;
    private final String displayTitle;

    VariantType(String displayName, String singularAccusative, String displayTitle) {
        this.displayName = displayName;
        this.singularAccusative = singularAccusative;
        this.displayTitle = displayTitle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSingularAccusative() {
        return singularAccusative;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }
}
