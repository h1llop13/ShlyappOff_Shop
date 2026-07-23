package com.shlyapoff.shop.service;

import com.shlyapoff.shop.bot.ShlyapOffBot;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.repository.AdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    @Mock
    private ShlyapOffBot bot;

    @Mock
    private AdminRepository adminRepository;

    private static final Long ADMIN_CHAT_ID = 999L;

    private TelegramNotificationService buildService() {
        TelegramNotificationService service = new TelegramNotificationService(bot, adminRepository);
        ReflectionTestUtils.setField(service, "superAdminChatId", ADMIN_CHAT_ID);
        ReflectionTestUtils.setField(service, "baseUrl", "https://test-shop.ru");
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

        when(adminRepository.findAll()).thenReturn(List.of());
        TelegramNotificationService service = buildService();
        service.notifyAdminAboutNewOrder(order);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessageWithButton(eq(ADMIN_CHAT_ID), messageCaptor.capture(), anyString(), anyString());

        String message = messageCaptor.getValue();
        assertThat(message).contains("https://t.me/ivan_the_customer");
        assertThat(message).contains("@ivan_the_customer");
    }

    @Test
    @DisplayName("если username не задан — показываем numeric Telegram ID")
    void fallsBackToTelegramIdWhenUsernameMissing() {
        Order order = buildOrder();
        order.setTelegramUserId(12345L);
        order.setTelegramUsername(null);

        when(adminRepository.findAll()).thenReturn(List.of());
        TelegramNotificationService service = buildService();
        service.notifyAdminAboutNewOrder(order);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessageWithButton(eq(ADMIN_CHAT_ID), messageCaptor.capture(), anyString(), anyString());

        String message = messageCaptor.getValue();
        assertThat(message).contains("12345");
        assertThat(message).doesNotContain("https://t.me/");
    }

    @Test
    @DisplayName("если нет Telegram данных — блок Telegram не показываем")
    void omitsTelegramBlockWhenNoTelegramDataAtAll() {
        Order order = buildOrder();
        order.setTelegramUserId(null);
        order.setTelegramUsername(null);

        when(adminRepository.findAll()).thenReturn(List.of());
        TelegramNotificationService service = buildService();
        service.notifyAdminAboutNewOrder(order);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessageWithButton(eq(ADMIN_CHAT_ID), messageCaptor.capture(), anyString(), anyString());

        assertThat(messageCaptor.getValue()).doesNotContain("Telegram");
    }

    @Test
    void acceptsOnlyPublicHttpsUrlsForInlineButtons() {
        assertThat(ShlyapOffBot.isPublicHttpsUrl("https://shop.example.com/admin/orders")).isTrue();
        assertThat(ShlyapOffBot.isPublicHttpsUrl("http://localhost:8080/admin/orders")).isFalse();
        assertThat(ShlyapOffBot.isPublicHttpsUrl("https://127.0.0.1/admin/orders")).isFalse();
        assertThat(ShlyapOffBot.isPublicHttpsUrl("not a URL")).isFalse();
    }
}
