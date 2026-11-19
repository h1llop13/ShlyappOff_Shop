package com.shlyapoff.shop.config;

import com.shlyapoff.shop.bot.ShlyapOffBot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Polling registration and menu updates recover independently after a network outage. */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TelegramBotInitializer {
    private final ShlyapOffBot shlyapOffBot;
    private final TelegramConfig telegramConfig;
    private final TelegramBotsApi botsApi;
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "telegram-registration");
        thread.setDaemon(true);
        return thread;
    });
    private BotSession session;
    private boolean menuConfigured;
    private boolean closed;

    @Bean
    static TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!StringUtils.hasText(telegramConfig.getBotToken())) {
            log.warn("Telegram включён, но TELEGRAM_BOT_TOKEN пуст. Polling не запущен.");
            return;
        }
        retryExecutor.scheduleWithFixedDelay(this::registerBot, 0, 30, TimeUnit.SECONDS);
    }

    public synchronized void registerBot() {
        if (closed) return;
        try {
            if (session == null || !session.isRunning()) {
                session = botsApi.registerBot(shlyapOffBot);
                log.info("Telegram-бот '{}' запущен (long polling).", shlyapOffBot.getBotUsername());
            }
            if (!menuConfigured) menuConfigured = shlyapOffBot.configureMenuButton();
        } catch (TelegramApiException | RuntimeException exception) {
            // Some client exceptions include the request URL with the bot token.
            log.warn("Telegram временно недоступен ({}). Повтор подключения через 30 секунд.",
                    exception.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public synchronized void stop() {
        closed = true;
        retryExecutor.shutdownNow();
        if (session != null && session.isRunning()) session.stop();
    }
}
