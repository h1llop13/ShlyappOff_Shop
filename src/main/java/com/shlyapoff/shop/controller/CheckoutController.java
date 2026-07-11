package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.dto.OrderDto;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class CheckoutController {

    private final OrderService orderService;

    /**
     * Показываем форму оформления заказа
     */
    @GetMapping("/checkout")
    public String checkoutPage(
            HttpServletRequest request,
            @RequestParam(required = false) String tgName, // Имя из Telegram (если есть)
            Model model) {

        String sessionId = request.getSession().getId();

        // Проверяем, что корзина не пуста
        var cartOpt = orderService.getCartForCheckout(sessionId);
        if (cartOpt.isEmpty()) {
            return "redirect:/cart";
        }

        // Если имя пришло из Telegram — подставляем в форму
        OrderDto orderDto = new OrderDto();
        if (tgName != null && !tgName.isBlank()) {
            orderDto.setCustomerName(tgName);
        }

        model.addAttribute("orderDto", orderDto);
        model.addAttribute("cart", cartOpt.get());

        // Считаем итоговую сумму
        double total = cartOpt.get().getItems().stream()
                .mapToDouble(item -> item.getProduct().getPrice().doubleValue() * item.getQuantity())
                .sum();
        model.addAttribute("total", total);

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
            @RequestParam(required = false) Long telegramUserId,
            @RequestParam(required = false) String telegramUsername,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Если есть ошибки валидации — возвращаем на форму
        if (bindingResult.hasErrors()) {
            String sessionId = request.getSession().getId();
            var cartOpt = orderService.getCartForCheckout(sessionId);
            if (cartOpt.isPresent()) {
                model.addAttribute("cart", cartOpt.get());
                double total = cartOpt.get().getItems().stream()
                        .mapToDouble(item -> item.getProduct().getPrice().doubleValue() * item.getQuantity())
                        .sum();
                model.addAttribute("total", total);
            }
            return "checkout";
        }

        try {
            String sessionId = request.getSession().getId();
            Order savedOrder = orderService.createOrderFromCart(
                    sessionId,
                    orderDto.getCustomerName(),
                    orderDto.getPhone(),
                    orderDto.getDeliveryType(),
                    orderDto.getComment(),
                    telegramUserId,
                    telegramUsername
            );

            // TODO: Здесь будет отправка уведомления в Telegram

            // Передаем ID заказа прямо в URL
            return "redirect:/success?orderId=" + savedOrder.getId();

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", "Корзина пуста");
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
}