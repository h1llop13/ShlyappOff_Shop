package com.shlyapoff.shop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Отдаёт "оболочку" страницы профиля Telegram Mini App.
 * Сами данные (история заказов, скидка) страница подгружает через
 * POST /api/profile/me, передавая window.Telegram.WebApp.initData —
 * см. {@link ProfileApiController}.
 */
@Controller
public class ProfileController {

    @GetMapping("/profile")
    public String profilePage() {
        return "profile";
    }
}
