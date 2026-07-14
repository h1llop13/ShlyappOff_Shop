package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.CustomUserDetailsService;
import com.shlyapoff.shop.service.TelegramAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class TelegramAuthController {

    private final TelegramAuthService telegramAuthService;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping("/auth/telegram-login")
    public String telegramLogin(
            @RequestParam("token") String token,
            // 1. ДОБАВЛЯЕМ этот параметр. required = false значит, что ссылка сработает и без него
            @RequestParam(value = "redirect", required = false) String redirect,
            HttpServletRequest request) {

        Optional<Long> chatIdOpt = telegramAuthService.validateAndConsumeToken(token);
        if (chatIdOpt.isEmpty()) {
            return "redirect:/login?error=expired";
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername("admin");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        // 2. ЛОГИКА РЕДИРЕКТА:
        // Проверяем, что redirect не null и начинается с "/" (защита от открытых редиректов на чужие сайты)
        if (redirect != null && redirect.startsWith("/")) {
            return "redirect:" + redirect; // Перенаправит на /admin/orders
        }

        // Запасной вариант, если параметра redirect нет в URL
        return "redirect:/admin";
    }
}