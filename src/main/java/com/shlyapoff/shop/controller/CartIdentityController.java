package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.CartService;
import com.shlyapoff.shop.service.TelegramCartSessionService;
import com.shlyapoff.shop.service.TelegramWebAppAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CartIdentityController {
    private final TelegramWebAppAuthService telegramWebAppAuthService;
    private final TelegramCartSessionService telegramCartSessionService;
    private final CartService cartService;

    public record BindRequest(String initData) {}
    public record BindResponse(boolean bound, boolean changed, String csrfToken) {}

    @GetMapping("/api/cart/session")
    public ResponseEntity<BindResponse> session(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean bound = session != null && telegramCartSessionService.getTelegramUserId(session) != null;
        return ResponseEntity.ok(new BindResponse(bound, false, null));
    }

    @PostMapping("/api/cart/bind")
    public ResponseEntity<BindResponse> bind(@RequestBody BindRequest request, HttpServletRequest servletRequest) {
        var user = telegramWebAppAuthService.validate(request.initData()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        HttpSession session = servletRequest.getSession();
        boolean changed;
        // Concurrent requests in one Mini App must not merge the same guest cart twice.
        synchronized (session) {
            Long previous = telegramCartSessionService.getTelegramUserId(session);
            if (previous != null && !Objects.equals(previous, user.id())) {
                return ResponseEntity.status(409).build();
            }
            changed = !Objects.equals(previous, user.id());
            if (changed) {
                cartService.bindTelegramCart(session.getId(), user.id());
                telegramCartSessionService.bind(session, user.id());
            }
        }
        CsrfToken csrf = (CsrfToken) servletRequest.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok(new BindResponse(true, changed, csrf == null ? null : csrf.getToken()));
    }
}
