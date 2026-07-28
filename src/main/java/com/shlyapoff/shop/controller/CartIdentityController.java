package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.CartService;
import com.shlyapoff.shop.service.TelegramCartSessionService;
import com.shlyapoff.shop.service.TelegramWebAppAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CartIdentityController {
    private final TelegramWebAppAuthService telegramWebAppAuthService;
    private final TelegramCartSessionService telegramCartSessionService;
    private final CartService cartService;

    public record BindRequest(String initData) {}
    public record BindResponse(boolean bound, boolean changed) {}

    @PostMapping("/api/cart/bind")
    public ResponseEntity<BindResponse> bind(@RequestBody BindRequest request, HttpServletRequest servletRequest) {
        var user = telegramWebAppAuthService.validate(request.initData()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        boolean changed = telegramCartSessionService.bind(servletRequest.getSession(), user.id());
        cartService.bindTelegramCart(servletRequest.getSession().getId(), user.id());
        return ResponseEntity.ok(new BindResponse(true, changed));
    }
}
