package com.shlyapoff.shop.bot;

import com.shlyapoff.shop.model.Admin;
import com.shlyapoff.shop.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Component
@RequiredArgsConstructor // Используем конструктор для внедрения зависимостей
@Slf4j
public class ShlyapOffBot extends TelegramLongPollingBot {

    private final AdminRepository adminRepository;

    @Value("${telegram.bot-token}")
    private String botToken;

    // Твой основной ID админа из конфига (как "супер-админ")
    @Value("${telegram.admin-chat-id}")
    private Long superAdminChatId;

    @Override
    public String getBotUsername() {
        return "ShlyapOffBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendMessage(chatId, "Привет! Я бот магазина ShlyapOff.");
            }

            // Логика добавления админа
            else if (messageText.startsWith("/add_admin")) {
                handleAddAdmin(chatId, messageText);
            }
        }
    }

    private void handleAddAdmin(long requesterChatId, String command) {
        // 1. Проверка прав: может ли этот человек добавлять админов?
        // Разрешаем, если это супер-админ из конфига ИЛИ если он уже есть в базе админов
        boolean isAuthorized = requesterChatId == superAdminChatId ||
                adminRepository.existsByTelegramChatId(requesterChatId);

        if (!isAuthorized) {
            sendMessage(requesterChatId, "❌ У вас нет прав для выполнения этой команды.");
            return;
        }

        // 2. Парсинг ID нового админа из команды (формат: /add_admin 123456789)
        String[] parts = command.split(" "); // <--- Вот здесь мы объявляем parts

        if (parts.length < 2) {
            sendMessage(requesterChatId, "⚠️ Использование: /add_admin [ID_пользователя]");
            return;
        }

        try {
            Long newAdminId = Long.parseLong(parts[1]); // <--- А здесь используем

            // 3. Проверка, нет ли его уже в базе
            if (adminRepository.existsByTelegramChatId(newAdminId)) {
                sendMessage(requesterChatId, "ℹ️ Этот пользователь уже является администратором.");
                return;
            }

            // 4. Сохранение
            Admin newAdmin = new Admin();
            newAdmin.setTelegramChatId(newAdminId);
            adminRepository.save(newAdmin);

            sendMessage(requesterChatId, "✅ Администратор с ID " + newAdminId + " успешно добавлен!");
            // Можно сразу отправить приветственное сообщение новому админу
            sendMessage(newAdminId, "👋 Привет! Теперь вы будете получать уведомления о новых заказах.");

        } catch (NumberFormatException e) {
            sendMessage(requesterChatId, "❌ Ошибка: ID должен состоять только из цифр.");
        }
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.enableHtml(true);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            // 3. Заменяем плохое e.printStackTrace() на правильное логирование
            log.error("Ошибка отправки простого сообщения в чат {}", chatId, e);
        }
    }

    public void sendMessageWithButton(Long chatId, String text, String buttonText, String buttonUrl) {
        if (!isPublicHttpsUrl(buttonUrl)) {
            log.warn("Refusing to send Telegram inline button with non-public URL: {}", buttonUrl);
            sendMessage(chatId, text);
            return;
        }

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.enableHtml(true);

        // Создаем кнопку
        InlineKeyboardButton urlButton = new InlineKeyboardButton();
        urlButton.setText(buttonText);
        urlButton.setUrl(buttonUrl); // Именно setUrl делает её кликабельной ссылкой

        // Клавиатура — это список рядов кнопок. У нас 1 ряд и 1 кнопка.
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(urlButton)));

        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения с кнопкой в чат {}", chatId, e);
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
