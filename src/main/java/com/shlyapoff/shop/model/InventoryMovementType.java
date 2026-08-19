package com.shlyapoff.shop.model;

public enum InventoryMovementType {
    INITIAL_STOCK("Начальный остаток"),
    MANUAL_ADJUSTMENT("Ручная корректировка"),
    CSV_IMPORT("Импорт CSV"),
    ORDER_RESERVATION("Резерв заказа"),
    RESERVATION_RELEASE("Возврат резерва");

    private final String displayName;

    InventoryMovementType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
