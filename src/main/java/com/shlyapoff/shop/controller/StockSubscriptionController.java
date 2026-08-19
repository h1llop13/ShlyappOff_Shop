package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.StockSubscriptionService;
import com.shlyapoff.shop.service.TelegramWebAppAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class StockSubscriptionController {
    private final TelegramWebAppAuthService telegramWebAppAuthService;
    private final StockSubscriptionService stockSubscriptionService;

    public record SubscriptionRequest(String initData) {}
    public record SubscriptionResponse(boolean subscribed, String message) {}

    @PostMapping("/api/products/{productId}/stock-subscription")
    public ResponseEntity<SubscriptionResponse> subscribe(@PathVariable Long productId,
                                                           @RequestBody SubscriptionRequest request) {
        var user = telegramWebAppAuthService.validate(request.initData()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        try {
            boolean created = stockSubscriptionService.subscribe(productId, user.id());
            return ResponseEntity.ok(new SubscriptionResponse(created,
                    created ? "Сообщим в Telegram, когда товар появится" : "Вы уже подписаны"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new SubscriptionResponse(false, exception.getMessage()));
        }
    }
}
