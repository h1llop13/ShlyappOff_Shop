package com.shlyapoff.shop.config;

import com.shlyapoff.shop.bot.ShlyapOffBot;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Регистрирует ShlyapOffBot в Telegram API при старте приложения.
 * Без этого шага бот никогда не начинает long polling и не получает
 * входящие апдейты (сообщения, команды типа /start, /add_admin и т.д.),
 * хотя отправка сообщений через execute() при этом продолжает работать,
 * так как для неё регистрация не требуется.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBotInitializer {

    private final ShlyapOffBot shlyapOffBot;

    @PostConstruct
    public void registerBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(shlyapOffBot);
            log.info("Telegram-бот '{}' успешно зарегистрирован и запущен (long polling).",
                    shlyapOffBot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Не удалось зарегистрировать Telegram-бота", e);
        }
    }
}