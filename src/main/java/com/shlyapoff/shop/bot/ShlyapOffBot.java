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

@Component
@RequiredArgsConstructor // Используем конструктор для внедрения зависимостей
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
            e.printStackTrace();
        }
    }
}