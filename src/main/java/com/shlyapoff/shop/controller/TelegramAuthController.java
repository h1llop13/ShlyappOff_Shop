package com.shlyapoff.shop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TelegramAuthController {

    @GetMapping("/auth/telegram-login")
    public String telegramLogin(
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(value = "redirect", required = false) String redirect) {
        return "redirect:/login";
    }
}