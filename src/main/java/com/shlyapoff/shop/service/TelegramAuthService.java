package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.TelegramLoginToken;
import com.shlyapoff.shop.repository.TelegramLoginTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TelegramAuthService {

    private final TelegramLoginTokenRepository tokenRepository;

    /**
     * Генерирует одноразовый токен для входа (живет 5 минут)
     */
    @Transactional
    public String generateLoginToken(Long telegramChatId) {
        // Очищаем старые просроченные токены (опционально, но полезно)
        // В продакшене лучше делать это по крону, но для начала сойдет

        String token = UUID.randomUUID().toString();
        TelegramLoginToken newToken = new TelegramLoginToken();
        newToken.setToken(token);
        newToken.setTelegramChatId(telegramChatId);
        newToken.setExpiresAt(LocalDateTime.now().plusMinutes(60)); // Токен живет 5 минут
        newToken.setUsed(false);

        tokenRepository.save(newToken);
        return token;
    }

    /**
     * Проверяет токен, помечает его как использованный и возвращает ChatId
     */
    @Transactional
    public Optional<Long> validateAndConsumeToken(String token) {
        Optional<TelegramLoginToken> tokenOpt = tokenRepository.findByTokenAndUsedFalse(token);

        if (tokenOpt.isPresent()) {
            TelegramLoginToken foundToken = tokenOpt.get();
            // Проверяем, не истек ли срок жизни
            if (foundToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                return Optional.empty(); // Токен протух
            }
            // Помечаем как использованный (защита от повторного входа по той же ссылке)
            foundToken.setUsed(true);
            tokenRepository.save(foundToken);
            return Optional.of(foundToken.getTelegramChatId());
        }
        return Optional.empty();
    }
}