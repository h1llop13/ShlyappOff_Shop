package com.shlyapoff.shop.service;

import com.shlyapoff.shop.bot.ShlyapOffBot;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Юнит-тесты для TelegramNotificationService.
 * Проверяем, что администратору уходит корректно оформленное сообщение,
 * в частности — что username клиента (для связи) подставляется как надо.
 */
@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    @Mock
    private ShlyapOffBot bot;

    private static final Long ADMIN_CHAT_ID = 999L;

    private TelegramNotificationService buildService() {
        TelegramNotificationService service = new TelegramNotificationService(bot);
        ReflectionTestUtils.setField(service, "adminChatId", ADMIN_CHAT_ID);
        return service;
    }

    private Order buildOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setCustomerName("Иван Иванов");
        order.setPhone("+79990001122");
        order.setDeliveryType("DELIVERY");
        order.setTotalAmount(new BigDecimal("450.50"));

        OrderItem item = new OrderItem();
        item.setProductName("Товар 1");
        item.setQuantity(2);
        item.setPriceAtMoment(new BigDecimal("100.00"));
        order.addItem(item);

        return order;
    }

    @Test
    @DisplayName("если у клиента есть username — в сообщении кликабельная ссылка на @username")
    void includesClickableUsernameWhenPresent() {
        Order order = buildOrder();
        order.setTelegramUserId(12345L);
        order.setTelegramUsername("ivan_the_customer");

        TelegramNotificationService service = buildService();
        service.notifyAdminAboutNewOrder(order);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(org.mockito.ArgumentMatchers.eq(ADMIN_CHAT_ID), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message).contains("https://t.me/ivan_the_customer");
        assertThat(message).contains("@ivan_the_customer");
    }

    @Test
    @DisplayName("если username не задан у пользователя — показываем numeric Telegram ID как запасной вариант")
    void fallsBackToTelegramIdWhenUsernameMissing() {
        Order order = buildOrder();
        order.setTelegramUserId(12345L);
        order.setTelegramUsername(null);

        TelegramNotificationService service = buildService();
        service.notifyAdminAboutNewOrder(order);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(org.mockito.ArgumentMatchers.eq(ADMIN_CHAT_ID), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message).contains("12345");
        assertThat(message).contains("username не указан");
        assertThat(message).doesNotContain("https://t.me/");
    }

    @Test
    @DisplayName("если ни username, ни telegram id нет (заказ оформлен не из Mini App) — блок Telegram не показываем")
    void omitsTelegramBlockWhenNoTelegramDataAtAll() {
        Order order = buildOrder();
        order.setTelegramUserId(null);
        order.setTelegramUsername(null);

        TelegramNotificationService service = buildService();
        service.notifyAdminAboutNewOrder(order);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(org.mockito.ArgumentMatchers.eq(ADMIN_CHAT_ID), messageCaptor.capture());

        assertThat(messageCaptor.getValue()).doesNotContain("Telegram");
    }
}