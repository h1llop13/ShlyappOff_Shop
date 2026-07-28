package com.shlyapoff.shop.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/** Хранит в веб-сессии только уже проверенный сервером идентификатор Telegram. */
@Service
public class TelegramCartSessionService {
    private static final String ATTRIBUTE = TelegramCartSessionService.class.getName() + ".telegramUserId";

    public Long getTelegramUserId(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        return value instanceof Long id ? id : null;
    }

    public boolean bind(HttpSession session, Long telegramUserId) {
        Long previous = getTelegramUserId(session);
        session.setAttribute(ATTRIBUTE, telegramUserId);
        return !telegramUserId.equals(previous);
    }
}
