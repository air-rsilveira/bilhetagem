package com.v.challenge.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class JwtTokenUtil {

    private static final String SECRET_KEY = "bilhetagem-secret-key-for-testing-purposes-only-32bytes!";

    public static String generateToken(String sub, String givenName,
                                       String familyName, String cpf,
                                       long expirationSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        long now = Instant.now().getEpochSecond();
        String payloadJson = String.format(
            "{\"sub\":\"%s\",\"given_name\":\"%s\",\"family_name\":\"%s\",\"cpf\":\"%s\",\"iat\":%d,\"exp\":%d}",
            sub, givenName, familyName, cpf, now, now + expirationSeconds);

        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String signature = computeHmac(header + "." + payload);

        return header + "." + payload + "." + signature;
    }

    public static String generateExpiredToken(String sub, String givenName,
                                              String familyName, String cpf) {
        return generateTokenWithExp(sub, givenName, familyName, cpf,
            Instant.now().getEpochSecond() - 3600);
    }

    public static String generateTokenWithExp(String sub, String givenName,
                                              String familyName, String cpf,
                                              long expTimestamp) {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        long now = Instant.now().getEpochSecond();
        String payloadJson = String.format(
            "{\"sub\":\"%s\",\"given_name\":\"%s\",\"family_name\":\"%s\",\"cpf\":\"%s\",\"iat\":%d,\"exp\":%d}",
            sub, givenName, familyName, cpf, now, expTimestamp);

        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String signature = computeHmac(header + "." + payload);

        return header + "." + payload + "." + signature;
    }

    private static String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao computar HMAC", e);
        }
    }
}
