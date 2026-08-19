package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReorderController {
    private final TelegramWebAppAuthService telegramWebAppAuthService;
    private final TelegramCartSessionService telegramCartSessionService;
    private final CustomerService customerService;
    private final ReorderService reorderService;

    public record RepeatOrderRequest(String initData) {}
    public record RepeatOrderResponse(boolean success, int addedItems, String message) {}

    @PostMapping("/api/profile/orders/{orderId}/repeat")
    public ResponseEntity<RepeatOrderResponse> repeat(@PathVariable Long orderId,
                                                      @RequestBody RepeatOrderRequest request,
                                                      HttpServletRequest servletRequest) {
        var user = telegramWebAppAuthService.validate(request.initData()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        var customer = customerService.findOrCreateByTelegram(
                user.id(), user.username(), user.firstName(), user.lastName());
        telegramCartSessionService.bind(servletRequest.getSession(), user.id());
        try {
            int added = reorderService.repeat(orderId, customer.getId(),
                    servletRequest.getSession().getId(), user.id());
            return ResponseEntity.ok(new RepeatOrderResponse(true, added, "Товары добавлены в корзину"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest()
                    .body(new RepeatOrderResponse(false, 0, exception.getMessage()));
        }
    }
}
