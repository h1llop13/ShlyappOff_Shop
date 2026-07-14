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
import com.shlyapoff.shop.service.TelegramAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@RequiredArgsConstructor // Используем конструктор для внедрения зависимостей
@Slf4j
public class ShlyapOffBot extends TelegramLongPollingBot {

    private final AdminRepository adminRepository;
    private final TelegramAuthService telegramAuthService;

    @Value("${telegram.bot-token}")
    private String botToken;

    // Твой основной ID админа из конфига (как "супер-админ")
    @Value("${telegram.admin-chat-id}")
    private Long superAdminChatId;

    @Value("${app.base-url}")
    private String baseUrl;

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

            else if (messageText.equals("/admin_login")) {
                handleAdminLogin(chatId);
            }

            // Логика добавления админа
            else if (messageText.startsWith("/add_admin")) {
                handleAddAdmin(chatId, messageText);
            }
        }
    }

    private void handleAdminLogin(long chatId) {
        // Проверяем, есть ли у пользователя права (супер-админ или есть в БД)
        boolean isAuthorized = chatId == superAdminChatId || adminRepository.existsByTelegramChatId(chatId);

        if (!isAuthorized) {
            sendMessage(chatId, "❌ У вас нет прав для входа в админку.");
            return;
        }

        // Генерируем токен
        String token = telegramAuthService.generateLoginToken(chatId);
        String loginUrl = baseUrl + "/auth/telegram-login?token=" + token;

        // Отправляем красивую HTML-ссылку
        String text = "🔐 <b>Вход в админ-панель</b>\n\n" +
                "Ссылка действительна 5 минут и сгорит после первого использования:\n" +
                "<a href=\"" + loginUrl + "\">👉 Войти в админку</a>";

        sendMessage(chatId, text);
    }

    // ... внутри класса ShlyapOffBot

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
}