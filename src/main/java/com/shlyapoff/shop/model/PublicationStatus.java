package com.shlyapoff.shop.model;

public enum PublicationStatus {
    DRAFT("Черновик"),
    SCHEDULED("Запланировано"),
    PUBLISHED("Опубликовано");

    private final String displayName;

    PublicationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
