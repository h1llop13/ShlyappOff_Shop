package com.shlyapoff.shop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "telegram")
public class TelegramConfig {
    /**
     * По умолчанию polling выключен: локальный запуск без секретов не должен
     * пытаться подключиться к Telegram.
     */
    private boolean enabled;
    private String botToken;
    private Long adminChatId;
    private String botUsername = "ShlyapOffBot";
    private String feedbackUrl = "https://t.me/h1llop";
}
