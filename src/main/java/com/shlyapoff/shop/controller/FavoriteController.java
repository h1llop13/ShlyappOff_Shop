package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.CustomerService;
import com.shlyapoff.shop.service.FavoriteService;
import com.shlyapoff.shop.service.TelegramWebAppAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FavoriteController {
    private final TelegramWebAppAuthService telegramWebAppAuthService;
    private final CustomerService customerService;
    private final FavoriteService favoriteService;

    public record FavoriteRequest(String initData, Long productId) {}
    public record FavoriteListResponse(List<Long> productIds) {}
    public record ToggleResponse(boolean favorite) {}

    @PostMapping("/api/favorites/me")
    public ResponseEntity<FavoriteListResponse> list(@RequestBody FavoriteRequest request) {
        var user = telegramWebAppAuthService.validate(request.initData()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        var customer = customerService.findOrCreateByTelegram(user.id(), user.username(), user.firstName(), user.lastName());
        return ResponseEntity.ok(new FavoriteListResponse(favoriteService.findProductIds(customer)));
    }

    @PostMapping("/api/favorites/toggle")
    public ResponseEntity<ToggleResponse> toggle(@RequestBody FavoriteRequest request) {
        var user = telegramWebAppAuthService.validate(request.initData()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        if (request.productId() == null) return ResponseEntity.badRequest().build();
        var customer = customerService.findOrCreateByTelegram(user.id(), user.username(), user.firstName(), user.lastName());
        return ResponseEntity.ok(new ToggleResponse(favoriteService.toggle(customer, request.productId())));
    }
}
