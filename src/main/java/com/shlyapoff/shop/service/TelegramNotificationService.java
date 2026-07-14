// src/main/java/com/shlyapoff/shop/service/TelegramNotificationService.java
package com.shlyapoff.shop.service;

import com.shlyapoff.shop.bot.ShlyapOffBot;
import com.shlyapoff.shop.model.Admin;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.shlyapoff.shop.service.TelegramAuthService;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final ShlyapOffBot bot;
    private final AdminRepository adminRepository;
    private final TelegramAuthService telegramAuthService;

    @Value("${telegram.admin-chat-id}")
    private Long superAdminChatId;

    @Value("${app.base-url}")
    private String baseUrl;

    public void notifyAdminAboutNewOrder(Order order) {
        String message = formatOrderMessage(order);
        String token = telegramAuthService.generateLoginToken(superAdminChatId);
        // собираем ссылку
        // после входа по токену контроллер редиректнет на /admin/orders
        String magicUrl = baseUrl + "/auth/telegram-login?token=" + token + "&redirect/admin/orders";

        // 1. Отправляем основному админу (из конфига)
        bot.sendMessageWithButton(superAdminChatId, message, "Открыть заказы", magicUrl);

        // 2. Отправляем всем остальным админам из базы
        List<Admin> admins = adminRepository.findAll();
        for (Admin admin : admins) {
            if (!admin.getTelegramChatId().equals(superAdminChatId)) {
                String adminToken = telegramAuthService.generateLoginToken(admin.getTelegramChatId());
                String adminUrl = baseUrl + "/auth/telegram-login?token=" + adminToken + "&redirect=/admin/orders";

                bot.sendMessageWithButton(admin.getTelegramChatId(), message, "📋 Открыть заказы", adminUrl);
            }
        }
    }

    private String formatOrderMessage(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("🛍 <b>НОВЫЙ ЗАКАЗ #").append(order.getId()).append("</b>\n\n");
        sb.append("👤 <b>Клиент:</b> ").append(order.getCustomerName()).append("\n");

        if (order.getTelegramUsername() != null && !order.getTelegramUsername().isBlank()) {
            sb.append("💬 <b>Telegram:</b> <a href=\"https://t.me/")
                    .append(order.getTelegramUsername()).append("\">@")
                    .append(order.getTelegramUsername()).append("</a>\n");
        } else if (order.getTelegramUserId() != null) {
            sb.append("💬 <b>Telegram ID:</b> <code>").append(order.getTelegramUserId()).append("</code>\n");
        }

        if (order.getPhone() != null && !order.getPhone().isBlank()) {
            sb.append("📱 <b>Телефон:</b> ").append(order.getPhone()).append("\n");
        }

        sb.append("📍 <b>Получение:</b> ").append(order.getDeliveryType()).append("\n");

        if (order.getComment() != null && !order.getComment().isBlank()) {
            sb.append("💬 <b>Комментарий:</b> <i>").append(order.getComment()).append("</i>\n");
        }

        sb.append("\n📦 <b>Состав:</b>\n");
        for (OrderItem item : order.getItems()) {
            BigDecimal itemTotal = item.getPriceAtMoment().multiply(BigDecimal.valueOf(item.getQuantity()));
            sb.append("• ").append(item.getProductName())
                    .append(" (").append(item.getQuantity()).append(" шт) — ")
                    .append(itemTotal).append(" ₽\n");
        }

        if (order.getDiscountPercent() != null && order.getDiscountPercent() > 0) {
            sb.append("\n🎁 <b>Скидка по программе лояльности:</b> -").append(order.getDiscountPercent()).append("%")
                    .append(" (было ").append(order.getSubtotalAmount()).append(" ₽)\n");
        }

        sb.append("\n💰 <b>ИТОГО: ").append(order.getTotalAmount()).append(" ₽</b>\n");

        if (order.getCreatedAt() != null) {
            String time = order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            sb.append("⏰ <i>").append(time).append("</i>");
        }

        return sb.toString();
    }
}