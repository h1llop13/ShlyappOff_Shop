package com.shlyapoff.shop.model;

import java.util.List;
import java.util.Locale;

/** Характеристики товара, доступные для конкретных категорий. */
public enum ProductField {
    BATTERY_CAPACITY("batteryCapacity", "Ёмкость аккумулятора", "Например: 650 мАч"),
    PUFF_COUNT("puffCount", "Количество тяг", "Например: 6000"),
    NICOTINE_STRENGTH("nicotineStrength", "Крепость никотина", "Например: 20 мг/мл"),
    VOLUME("volume", "Объём банки", "Например: 30 мл"),
    CARTRIDGE_VOLUME("cartridgeVolume", "Объём картриджа", "Например: 2 мл"),
    MAX_POWER("maxPower", "Максимальная мощность", "Например: 30 Вт"),
    PACKAGE_QUANTITY("packageQuantity", "Количество в упаковке", "Например: 5 шт.");

    private final String propertyName;
    private final String displayName;
    private final String placeholder;

    ProductField(String propertyName, String displayName, String placeholder) {
        this.propertyName = propertyName;
        this.displayName = displayName;
        this.placeholder = placeholder;
    }

    public String getPropertyName() { return propertyName; }
    public String getDisplayName() { return displayName; }
    public String getPlaceholder() { return placeholder; }

    public String getValue(Product product) {
        return switch (this) {
            case BATTERY_CAPACITY -> product.getBatteryCapacity();
            case PUFF_COUNT -> product.getPuffCount();
            case NICOTINE_STRENGTH -> product.getNicotineStrength();
            case VOLUME -> product.getVolume();
            case CARTRIDGE_VOLUME -> product.getCartridgeVolume();
            case MAX_POWER -> product.getMaxPower();
            case PACKAGE_QUANTITY -> product.getPackageQuantity();
        };
    }

    public static List<ProductField> forCategory(Category category) {
        if (category == null || category.getName() == null) return List.of();

        return switch (category.getName().trim().toLowerCase(Locale.ROOT)) {
            case "одноразки" -> List.of(BATTERY_CAPACITY, PUFF_COUNT, NICOTINE_STRENGTH);
            case "жидкости" -> List.of(NICOTINE_STRENGTH, VOLUME);
            case "под-системы", "под системы" -> List.of(BATTERY_CAPACITY, CARTRIDGE_VOLUME, MAX_POWER);
            case "шайбы", "пластинки" -> List.of(NICOTINE_STRENGTH);
            case "расходники" -> List.of(PACKAGE_QUANTITY);
            default -> List.of();
        };
    }
}
