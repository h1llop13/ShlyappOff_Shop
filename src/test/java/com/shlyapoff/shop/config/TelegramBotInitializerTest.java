package com.shlyapoff.shop.config;

import com.shlyapoff.shop.bot.ShlyapOffBot;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;

import static org.mockito.Mockito.*;

class TelegramBotInitializerTest {
    @Test
    void registrationRecoversAndDoesNotDuplicatePollingWhenMenuFails() throws Exception {
        ShlyapOffBot bot = mock(ShlyapOffBot.class);
        TelegramBotsApi api = mock(TelegramBotsApi.class);
        BotSession session = mock(BotSession.class);
        when(api.registerBot(bot)).thenThrow(new TelegramApiException("offline")).thenReturn(session);
        when(session.isRunning()).thenReturn(true);
        when(bot.configureMenuButton()).thenThrow(new TelegramApiException("offline")).thenReturn(true);
        TelegramBotInitializer initializer = new TelegramBotInitializer(bot, new TelegramConfig(), api);
        try {
            initializer.registerBot(); // Registration failed.
            initializer.registerBot(); // Polling started, menu failed.
            initializer.registerBot(); // Only the menu is retried.
            initializer.registerBot(); // Already running, no duplicate registration.
            verify(api, times(2)).registerBot(bot);
            verify(bot, times(2)).configureMenuButton();
        } finally { initializer.stop(); }
        verify(session).stop();
        initializer.registerBot();
        verify(api, times(2)).registerBot(bot);
    }

    @Test
    void sessionCookiesSupportHttpsEmbeddingAndHttpLocalAdmin() {
        var supplier = new WebConfig().sessionCookieSameSiteSupplier();
        var cookie = new jakarta.servlet.http.Cookie("JSESSIONID", "test");
        org.assertj.core.api.Assertions.assertThat(supplier.getSameSite(cookie))
                .isEqualTo(org.springframework.boot.web.server.Cookie.SameSite.LAX);
        cookie.setSecure(true);
        org.assertj.core.api.Assertions.assertThat(supplier.getSameSite(cookie))
                .isEqualTo(org.springframework.boot.web.server.Cookie.SameSite.NONE);
    }
}
