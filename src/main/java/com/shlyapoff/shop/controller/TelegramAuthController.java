package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.CustomUserDetailsService;
import com.shlyapoff.shop.service.TelegramAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class TelegramAuthController {

    private final TelegramAuthService telegramAuthService;
    private final CustomUserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository; // ← вернуть!

    @GetMapping("/auth/telegram-login")
    public String telegramLogin(
            @RequestParam("token") String token,
            @RequestParam(value = "redirect", required = false) String redirect,
            HttpServletRequest request,
            HttpServletResponse response) { // ← вернуть!

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

        // Правильный способ для Spring Security 6+
        securityContextRepository.saveContext(context, request, response); // ← вернуть!

        if (redirect != null && redirect.startsWith("/")) {
            return "redirect:" + redirect;
        }
        return "redirect:/admin";
    }
}