package com.shlyapoff.shop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Проверяет подлинность initData, которую Telegram Mini App передаёт на фронтенде
 * (window.Telegram.WebApp.initData), по алгоритму из документации Telegram:
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
 * <p>
 * Это нужно, чтобы нельзя было подменить telegramUserId и посмотреть чужой профиль/заказы.
 */
@Service
@RequiredArgsConstructor
public class TelegramWebAppAuthService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long MAX_AUTH_AGE_SECONDS = 60 * 60;
    private static final long MAX_FUTURE_CLOCK_SKEW_SECONDS = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${telegram.bot-token}")
    private String botToken;

    public record TelegramWebAppUser(Long id, String username, String firstName, String lastName) {
    }

    /**
     * Возвращает данные пользователя, если initData валидна (подпись совпала и она не протухла).
     */
    public Optional<TelegramWebAppUser> validate(String initData) {
        if (initData == null || initData.isBlank()) {
            return Optional.empty();
        }

        Map<String, String> params = parseQueryString(initData);
        String receivedHash = params.remove("hash");
        if (receivedHash == null) {
            return Optional.empty();
        }

        String dataCheckString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        try {
            byte[] secretKey = hmacSha256(botToken.getBytes(StandardCharsets.UTF_8), "WebAppData".getBytes(StandardCharsets.UTF_8));
            byte[] computedHash = hmacSha256(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);
            String computedHashHex = toHex(computedHash);

            if (!constantTimeEquals(computedHashHex, receivedHash)) {
                return Optional.empty();
            }

            String authDateStr = params.get("auth_date");
            if (authDateStr == null) {
                return Optional.empty();
            }

            long authDate = Long.parseLong(authDateStr);
            long ageSeconds = Instant.now().getEpochSecond() - authDate;
            if (ageSeconds > MAX_AUTH_AGE_SECONDS || ageSeconds < -MAX_FUTURE_CLOCK_SKEW_SECONDS) {
                return Optional.empty();
            }

            String userJson = params.get("user");
            if (userJson == null) {
                return Optional.empty();
            }

            Map<?, ?> userMap = objectMapper.readValue(userJson, Map.class);
            Long id = ((Number) userMap.get("id")).longValue();
            String username = (String) userMap.get("username");
            String firstName = (String) userMap.get("first_name");
            String lastName = (String) userMap.get("last_name");

            return Optional.of(new TelegramWebAppUser(id, username, firstName, lastName));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Map<String, String> parseQueryString(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isBlank()) continue;
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(key, HMAC_SHA256));
        return mac.doFinal(data);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
