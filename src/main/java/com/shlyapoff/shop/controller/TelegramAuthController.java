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
            HttpServletRequest request) {

        // 1. Проверяем и "сжигаем" токен
        Optional<Long> chatIdOpt = telegramAuthService.validateAndConsumeToken(token);

        if (chatIdOpt.isEmpty()) {
            // Если токен невалиден, протух или уже использован
            return "redirect:/login?error=expired";
        }

        // 2. Токен валиден. Находим нашего веб-пользователя (админа) в БД.
        // Так как у нас пока один базовый админ, логинимся под ним.
        // (В будущем можно связать chatId и username в таблице users)
        UserDetails userDetails = userDetailsService.loadUserByUsername("admin");

        // 3. Создаем объект авторизации для Spring Security
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        // 4. Сохраняем авторизацию в контекст безопасности и в сессию
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        // Стандартный атрибут, который читает Spring Security Filter
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        // 5. Успешный вход! Редиректим в админку
        return "redirect:/admin";
    }
}