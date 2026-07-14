package com.shlyapoff.shop.service;

import com.shlyapoff.shop.bot.ShlyapOffBot;
import com.shlyapoff.shop.model.Admin;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final ShlyapOffBot bot;
    private final AdminRepository adminRepository;
    // ← TelegramAuthService убран полностью

    @Value("${telegram.admin-chat-id}")
    private Long superAdminChatId;

    @Value("${app.base-url}")
    private String baseUrl;

    public void notifyAdminAboutNewOrder(Order order) {
        String message = formatOrderMessage(order);
        String ordersUrl = baseUrl + "/admin/orders";

        // Просто кнопка со ссылкой — открывать в обычном браузере
        bot.sendMessageWithButton(superAdminChatId, message, "📋 Открыть заказы", ordersUrl);

        List<Admin> admins = adminRepository.findAll();
        for (Admin admin : admins) {
            if (!admin.getTelegramChatId().equals(superAdminChatId)) {
                bot.sendMessageWithButton(admin.getTelegramChatId(), message, "📋 Открыть заказы", ordersUrl);
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
            sb.append("\n🎁 <b>Скидка:</b> -").append(order.getDiscountPercent()).append("%")
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