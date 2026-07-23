package com.shlyapoff.shop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramWebAppAuthServiceTest {

    private static final String BOT_TOKEN = "123456:test-token";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private TelegramWebAppAuthService service;

    @BeforeEach
    void setUp() {
        service = new TelegramWebAppAuthService();
        ReflectionTestUtils.setField(service, "botToken", BOT_TOKEN);
    }

    @Test
    void acceptsRecentSignedInitData() throws Exception {
        String initData = signedInitData(Instant.now().getEpochSecond() - 60);

        var result = service.validate(initData);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo(12345L);
        assertThat(result.orElseThrow().username()).isEqualTo("verified_user");
    }

    @Test
    void rejectsTelegramUserIdChangedAfterSigning() throws Exception {
        String signedInitData = signedInitData(Instant.now().getEpochSecond() - 60);
        String tamperedInitData = signedInitData.replace("12345", "99999");

        assertThat(service.validate(tamperedInitData)).isEmpty();
    }

    @Test
    void rejectsSignedInitDataWithoutAuthDate() throws Exception {
        assertThat(service.validate(signedInitData(null))).isEmpty();
    }

    @Test
    void rejectsExpiredSignedInitData() throws Exception {
        long twoHoursAgo = Instant.now().minusSeconds(2 * 60 * 60).getEpochSecond();

        assertThat(service.validate(signedInitData(twoHoursAgo))).isEmpty();
    }

    @Test
    void rejectsSignedInitDataFromFuture() throws Exception {
        long fiveMinutesAhead = Instant.now().plusSeconds(5 * 60).getEpochSecond();

        assertThat(service.validate(signedInitData(fiveMinutesAhead))).isEmpty();
    }

    private String signedInitData(Long authDate) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query_id", "AAEAA-test-query");
        if (authDate != null) {
            params.put("auth_date", authDate.toString());
        }
        params.put("user", "{\"id\":12345,\"first_name\":\"Ivan\",\"username\":\"verified_user\"}");

        String dataCheckString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        byte[] secretKey = hmac(BOT_TOKEN.getBytes(StandardCharsets.UTF_8), "WebAppData".getBytes(StandardCharsets.UTF_8));
        String hash = toHex(hmac(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey));

        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return query + "&hash=" + hash;
    }

    private byte[] hmac(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(key, HMAC_SHA256));
        return mac.doFinal(data);
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
