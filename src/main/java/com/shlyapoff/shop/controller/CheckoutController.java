package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.dto.OrderDto;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.service.OrderService;
import com.shlyapoff.shop.service.TelegramWebAppAuthService;
import com.shlyapoff.shop.service.TelegramCartSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
public class CheckoutController {

    private final OrderService orderService;
    private final TelegramWebAppAuthService telegramWebAppAuthService;
    private final TelegramCartSessionService telegramCartSessionService;

    @Autowired
    public CheckoutController(OrderService orderService, TelegramWebAppAuthService telegramWebAppAuthService,
                              TelegramCartSessionService telegramCartSessionService) {
        this.orderService = orderService;
        this.telegramWebAppAuthService = telegramWebAppAuthService;
        this.telegramCartSessionService = telegramCartSessionService;
    }

    /** Совместимость с существующими изолированными тестами контроллера. */
    public CheckoutController(OrderService orderService, TelegramWebAppAuthService telegramWebAppAuthService) {
        this(orderService, telegramWebAppAuthService, new TelegramCartSessionService());
    }

    /**
     * Показываем форму оформления заказа
     */
    @GetMapping("/checkout")
    public String checkoutPage(
            HttpServletRequest request,
            @RequestParam(required = false) String tgName, // Имя из Telegram (если есть)
            Model model) {

        if (!populateCheckoutModel(request.getSession().getId(), telegramCartSessionService.getTelegramUserId(request.getSession()), model)) {
            return "redirect:/cart";
        }

        // Если имя пришло из Telegram — подставляем в форму
        OrderDto orderDto = new OrderDto();
        if (tgName != null && !tgName.isBlank()) {
            orderDto.setCustomerName(tgName);
        }

        model.addAttribute("orderDto", orderDto);
        Long telegramUserId = telegramCartSessionService.getTelegramUserId(request.getSession());
        BigDecimal bonusBalance = telegramUserId == null ? BigDecimal.ZERO
                : orderService.findBonusBalance(telegramUserId);
        model.addAttribute("bonusBalance", bonusBalance);

        return "checkout";
    }

    /**
     * Обрабатываем отправку формы
     */
    @PostMapping("/checkout")
    public String processOrder(
            @Valid @ModelAttribute("orderDto") OrderDto orderDto,
            BindingResult bindingResult,
            HttpServletRequest request,
            @RequestParam(required = false) String telegramInitData,
            Model model,
            RedirectAttributes redirectAttributes) {

        TelegramWebAppAuthService.TelegramWebAppUser telegramUser = null;
        if (StringUtils.hasText(telegramInitData)) {
            telegramUser = telegramWebAppAuthService.validate(telegramInitData).orElse(null);
            if (telegramUser == null) {
                bindingResult.reject(
                        "telegram.auth.invalid",
                        "Не удалось подтвердить данные Telegram. Откройте магазин заново через бота."
                );
            }
            else {
                telegramCartSessionService.bind(request.getSession(), telegramUser.id());
                orderService.bindTelegramCart(request.getSession().getId(), telegramUser.id());
            }
        }

        // Если есть ошибки валидации — возвращаем на форму
        if (bindingResult.hasErrors()) {
            populateCheckoutModel(request.getSession().getId(), telegramCartSessionService.getTelegramUserId(request.getSession()), model);
            return "checkout";
        }

        try {
            String sessionId = request.getSession().getId();
            Order savedOrder = orderService.createOrderFromCart(
                    sessionId, orderDto.getCustomerName(), orderDto.getPhone(), orderDto.getDeliveryType(),
                    orderDto.getComment(), telegramUser != null ? telegramUser.id() : null,
                    telegramUser != null ? telegramUser.username() : null, orderDto.isUseBonuses(),
                    orderDto.getPromoCode());

            // TODO: Здесь будет отправка уведомления в Telegram

            // Передаем ID заказа прямо в URL
            return "redirect:/success?orderId=" + savedOrder.getId();

        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart";
        }
    }

    /**
     * Страница успеха
     */
    @GetMapping("/success")
    public String successPage(
            @RequestParam(required = false) Long orderId,
            Model model) {
        if (orderId == null) {
            return "redirect:/";
        }
        model.addAttribute("orderId", orderId);
        return "success";
    }

    private boolean populateCheckoutModel(String sessionId, Long telegramUserId, Model model) {
        var cartOpt = orderService.getCartForCheckout(sessionId, telegramUserId);
        if (cartOpt.isEmpty()) {
            return false;
        }

        var cart = cartOpt.get();
        model.addAttribute("cart", cart);
        double total = cart.getItems().stream()
                .mapToDouble(item -> item.getProduct().getPrice().doubleValue() * item.getQuantity())
                .sum();
        model.addAttribute("total", total);
        return true;
    }
}
