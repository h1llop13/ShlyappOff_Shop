package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.dto.OrderDto;
import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.CartItem;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.service.OrderService;
import com.shlyapoff.shop.service.TelegramWebAppAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class CheckoutControllerTest {

    private static final String SESSION_ID = "checkout-session";

    private OrderService orderService;
    private TelegramWebAppAuthService telegramWebAppAuthService;
    private CheckoutController controller;
    private HttpServletRequest request;
    private Cart cart;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        telegramWebAppAuthService = mock(TelegramWebAppAuthService.class);
        controller = new CheckoutController(orderService, telegramWebAppAuthService);

        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
        request = mock(HttpServletRequest.class);
        when(request.getSession()).thenReturn(session);

        Product product = new Product();
        product.setPrice(new BigDecimal("150.00"));
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(2);
        cart = new Cart();
        cart.getItems().add(item);
    }

    @Test
    void rejectsForgedTelegramInitData() {
        OrderDto orderDto = validOrderDto();
        var bindingResult = new BeanPropertyBindingResult(orderDto, "orderDto");
        when(telegramWebAppAuthService.validate("forged-data")).thenReturn(Optional.empty());
        when(orderService.getCartForCheckout(SESSION_ID)).thenReturn(Optional.of(cart));

        String view = controller.processOrder(
                orderDto,
                bindingResult,
                request,
                "forged-data",
                new ConcurrentModel(),
                new RedirectAttributesModelMap()
        );

        assertThat(view).isEqualTo("checkout");
        assertThat(bindingResult.getGlobalError()).isNotNull();
        verify(orderService, never()).createOrderFromCart(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void usesOnlyVerifiedTelegramIdentity() {
        OrderDto orderDto = validOrderDto();
        var bindingResult = new BeanPropertyBindingResult(orderDto, "orderDto");
        var verifiedUser = new TelegramWebAppAuthService.TelegramWebAppUser(
                12345L,
                "verified_user",
                "Ivan",
                null
        );
        Order savedOrder = new Order();
        savedOrder.setId(42L);

        when(telegramWebAppAuthService.validate("signed-data")).thenReturn(Optional.of(verifiedUser));
        when(orderService.createOrderFromCart(
                eq(SESSION_ID), any(), any(), any(), any(), eq(12345L), eq("verified_user")
        )).thenReturn(savedOrder);

        String view = controller.processOrder(
                orderDto,
                bindingResult,
                request,
                "signed-data",
                new ConcurrentModel(),
                new RedirectAttributesModelMap()
        );

        assertThat(view).isEqualTo("redirect:/success?orderId=42");
        verify(orderService).createOrderFromCart(
                SESSION_ID,
                orderDto.getCustomerName(),
                orderDto.getPhone(),
                orderDto.getDeliveryType(),
                orderDto.getComment(),
                12345L,
                "verified_user"
        );
    }

    @Test
    void createsGuestOrderWhenTelegramInitDataIsAbsent() {
        OrderDto orderDto = validOrderDto();
        var bindingResult = new BeanPropertyBindingResult(orderDto, "orderDto");
        Order savedOrder = new Order();
        savedOrder.setId(43L);
        when(orderService.createOrderFromCart(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(savedOrder);

        String view = controller.processOrder(
                orderDto,
                bindingResult,
                request,
                null,
                new ConcurrentModel(),
                new RedirectAttributesModelMap()
        );

        assertThat(view).isEqualTo("redirect:/success?orderId=43");
        verify(telegramWebAppAuthService, never()).validate(any());
        verify(orderService).createOrderFromCart(
                SESSION_ID,
                orderDto.getCustomerName(),
                orderDto.getPhone(),
                orderDto.getDeliveryType(),
                orderDto.getComment(),
                null,
                null
        );
    }

    private OrderDto validOrderDto() {
        OrderDto dto = new OrderDto();
        dto.setCustomerName("Иван");
        dto.setPhone("+79990000000");
        dto.setDeliveryType("Самовывоз");
        dto.setComment("Без звонка");
        return dto;
    }
}
