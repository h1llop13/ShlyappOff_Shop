package com.shlyapoff.shop.bot;

import com.shlyapoff.shop.config.TelegramConfig;
import com.shlyapoff.shop.model.Admin;
import com.shlyapoff.shop.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor // Используем конструктор для внедрения зависимостей
@Slf4j
public class ShlyapOffBot extends TelegramLongPollingBot {

    private final AdminRepository adminRepository;
    private final TelegramConfig telegramConfig;

    @Value("${app.base-url}")
    private String baseUrl;

    @Override
    public String getBotUsername() {
        return StringUtils.hasText(telegramConfig.getBotUsername())
                ? telegramConfig.getBotUsername()
                : "ShlyapOffBot";
    }

    @Override
    public String getBotToken() {
        return telegramConfig.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (isCommand(messageText, "/start") || isCommand(messageText, "/help")) {
                sendWelcomeMessage(chatId);
            }
            else if (isCommand(messageText, "/support")) {
                sendSupportMessage(chatId);
            }
            else if (isCommand(messageText, "/myid")) {
                sendMessage(chatId, "Ваш Telegram ID: <code>" + chatId + "</code>");
            }
            else if (isCommand(messageText, "/add_admin")) {
                handleAddAdmin(chatId, messageText);
            }
        }
    }

    private void handleAddAdmin(long requesterChatId, String command) {
        if (!Objects.equals(requesterChatId, telegramConfig.getAdminChatId())) {
            sendMessage(requesterChatId, "❌ У вас нет прав для выполнения этой команды.");
            return;
        }

        String[] parts = command.trim().split("\\s+");

        if (parts.length < 2) {
            sendMessage(requesterChatId, "⚠️ Использование: /add_admin ID_пользователя\n"
                    + "Пользователь может узнать свой ID командой /myid.");
            return;
        }

        try {
            Long newAdminId = Long.parseLong(parts[1]); // <--- А здесь используем

            if (adminRepository.existsByTelegramChatId(newAdminId)) {
                sendMessage(requesterChatId, "ℹ️ Этот пользователь уже является администратором.");
                return;
            }

            Admin newAdmin = new Admin();
            newAdmin.setTelegramChatId(newAdminId);
            adminRepository.save(newAdmin);

            sendMessage(requesterChatId, "✅ Администратор с ID " + newAdminId + " успешно добавлен!");
            if (!sendMessage(newAdminId, "👋 Привет! Теперь вы будете получать уведомления о новых заказах.")) {
                sendMessage(requesterChatId,
                        "⚠️ Администратор добавлен, но ещё не начал чат с ботом. "
                                + "Пусть сначала отправит /start.");
            }

        } catch (NumberFormatException e) {
            sendMessage(requesterChatId, "❌ Ошибка: ID должен состоять только из цифр.");
        }
    }

    public boolean sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.enableHtml(true);
        try {
            execute(message);
            return true;
        } catch (TelegramApiException e) {
            // 3. Заменяем плохое e.printStackTrace() на правильное логирование
            log.error("Ошибка отправки простого сообщения в чат {}", chatId, e);
            return false;
        }
    }

    private void sendWelcomeMessage(long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (isPublicHttpsUrl(baseUrl)) {
            InlineKeyboardButton storeButton = new InlineKeyboardButton();
            storeButton.setText("🛍 Открыть магазин");
            storeButton.setWebApp(new WebAppInfo(baseUrl));
            rows.add(List.of(storeButton));
        } else {
            log.warn("APP_BASE_URL is not a public HTTPS URL; Mini App button is hidden: {}", baseUrl);
        }

        String feedbackUrl = telegramConfig.getFeedbackUrl();
        if (isPublicHttpsUrl(feedbackUrl)) {
            InlineKeyboardButton supportButton = new InlineKeyboardButton();
            supportButton.setText("💬 Поддержка");
            supportButton.setUrl(feedbackUrl);
            rows.add(List.of(supportButton));
        }

        String text = "Привет! Это ShlyapOff — магазин в Telegram.\n"
                + "Открой каталог, добавь товары в корзину и оформи заказ прямо здесь.";
        if (rows.isEmpty()) {
            sendMessage(chatId, text + "\n\nМагазин сейчас временно недоступен.");
            return;
        }
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private void sendSupportMessage(long chatId) {
        String feedbackUrl = telegramConfig.getFeedbackUrl();
        String text = "Если есть вопрос по заказу или товару — напишите нам, поможем.";
        if (!isPublicHttpsUrl(feedbackUrl)) {
            sendMessage(chatId, text);
            return;
        }
        sendMessageWithButton(chatId, text, "💬 Связаться с нами", feedbackUrl);
    }

    private boolean isCommand(String messageText, String command) {
        String receivedCommand = messageText.split("\\s+", 2)[0];
        return receivedCommand.equals(command)
                || receivedCommand.equals(command + "@" + getBotUsername());
    }

    public boolean sendMessageWithButton(Long chatId, String text, String buttonText, String buttonUrl) {
        if (!isPublicHttpsUrl(buttonUrl)) {
            log.warn("Refusing to send Telegram inline button with non-public URL: {}", buttonUrl);
            return sendMessage(chatId, text);
        }

        InlineKeyboardButton urlButton = new InlineKeyboardButton();
        urlButton.setText(buttonText);
        urlButton.setUrl(buttonUrl);

        return sendMessageWithKeyboard(chatId, text, List.of(List.of(urlButton)));
    }

    private boolean sendMessageWithKeyboard(Long chatId, String text, List<List<InlineKeyboardButton>> rows) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.enableHtml(true);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        message.setReplyMarkup(markup);

        try {
            execute(message);
            return true;
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения с кнопкой в чат {}", chatId, e);
            return false;
        }
    }

    public static boolean isPublicHttpsUrl(String url) {
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !"localhost".equalsIgnoreCase(uri.getHost())
                    && !"127.0.0.1".equals(uri.getHost())
                    && !"::1".equals(uri.getHost());
        } catch (URISyntaxException | NullPointerException ex) {
            return false;
        }
    }
}
