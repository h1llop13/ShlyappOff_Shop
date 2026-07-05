package com.shlyapoff.shop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "telegram")
public class TelegramConfig {
    private String botToken;
    private Long adminChatId;
}
