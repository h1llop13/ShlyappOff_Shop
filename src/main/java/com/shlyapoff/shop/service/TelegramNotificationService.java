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

    @Value("${telegram.admin-chat-id}")
    private Long superAdminChatId;

    @Value("${app.base-url}")
    private String baseUrl;

    public void notifyAdminAboutNewOrder(Order order) {
        String message = formatOrderMessage(order);
        String ordersUrl = baseUrl + "/admin/orders";

        // Просто кнопка со ссылкой — открывать в обычном браузере
        boolean delivered = bot.sendMessageWithButton(superAdminChatId, message, "📋 Открыть заказы", ordersUrl);

        List<Admin> admins = adminRepository.findAll();
        for (Admin admin : admins) {
            if (!admin.getTelegramChatId().equals(superAdminChatId)) {
                delivered = bot.sendMessageWithButton(admin.getTelegramChatId(), message, "📋 Открыть заказы", ordersUrl) && delivered;
            }
        }
        if (!delivered) {
            throw new IllegalStateException("Telegram временно недоступен");
        }
    }

    private String escape(String value, int maxCodePoints) {
        if (value == null) return "";
        if (value.codePointCount(0, value.length()) > maxCodePoints) {
            value = value.substring(0, value.offsetByCodePoints(0, maxCodePoints)) + "…";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String formatOrderMessage(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("🛍 <b>НОВЫЙ ЗАКАЗ #").append(order.getId()).append("</b>\n\n");
        sb.append("👤 <b>Клиент:</b> ").append(escape(order.getCustomerName(), 100)).append("\n");

        if (order.getTelegramUsername() != null && order.getTelegramUsername().matches("[A-Za-z0-9_]{1,64}")) {
            sb.append("💬 <b>Telegram:</b> <a href=\"https://t.me/")
                    .append(order.getTelegramUsername()).append("\">@")
                    .append(order.getTelegramUsername()).append("</a>\n");
        } else if (order.getTelegramUserId() != null) {
            sb.append("💬 <b>Telegram ID:</b> <code>").append(order.getTelegramUserId()).append("</code>\n");
        }

        if (order.getPhone() != null && !order.getPhone().isBlank()) {
            sb.append("📱 <b>Телефон:</b> ").append(escape(order.getPhone(), 20)).append("\n");
        }

        sb.append("📍 <b>Получение:</b> ").append(escape(order.getDeliveryType(), 30)).append("\n");

        if (order.getComment() != null && !order.getComment().isBlank()) {
            sb.append("💬 <b>Комментарий:</b> <i>").append(escape(order.getComment(), 200)).append("</i>\n");
        }

        sb.append("\n📦 <b>Состав:</b>\n");
        int shownItems = 0;
        for (OrderItem item : order.getItems()) {
            if (sb.length() > 2800) {
                sb.append("… Ещё позиций: ").append(order.getItems().size() - shownItems)
                        .append(". Полный состав — в админке.\n");
                break;
            }
            shownItems++;
            BigDecimal itemTotal = item.getPriceAtMoment().multiply(BigDecimal.valueOf(item.getQuantity()));
            sb.append("• ").append(escape(item.getProductName(), 100))
                    .append(" (").append(item.getQuantity()).append(" шт) — ")
                    .append(itemTotal).append(" ₽\n");
        }

        if (order.getBonusesSpent() != null && order.getBonusesSpent().signum() > 0) {
            sb.append("\n🎁 <b>Списано бонусов:</b> ").append(order.getBonusesSpent()).append(" ₽")
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
