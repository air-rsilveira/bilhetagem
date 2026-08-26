package com.v.challenge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String SECRET_KEY = "bilhetagem-secret-key-for-testing-purposes-only-32bytes!";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/api/v1/cobrancas/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token == null) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return;
            }

            UserContext userContext = validateAndExtract(token);
            if (userContext == null) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return;
            }

            UserContextHolder.setContext(userContext);

            Authentication auth = new UsernamePasswordAuthenticationToken(
                userContext.idUsuario(), null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private UserContext validateAndExtract(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String headerAndPayload = parts[0] + "." + parts[1];
            String signature = parts[2];

            // Valida assinatura
            String expectedSignature = computeHmac(headerAndPayload);
            if (!expectedSignature.equals(signature)) {
                return null;
            }

            // Decodifica payload
            String payloadJson = new String(
                Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            // Parse manual do JSON (sem dependência externa)
            String sub = extractJsonValue(payloadJson, "sub");
            String givenName = extractJsonValue(payloadJson, "given_name");
            String familyName = extractJsonValue(payloadJson, "family_name");
            String cpf = extractJsonValue(payloadJson, "cpf");
            String expStr = extractJsonValue(payloadJson, "exp");

            // Valida expiração
            if (expStr != null) {
                long exp = Long.parseLong(expStr);
                if (exp < System.currentTimeMillis() / 1000) {
                    return null;
                }
            }

            if (sub == null) {
                return null;
            }

            return new UserContext(sub, givenName, familyName, cpf);
        } catch (Exception e) {
            return null;
        }
    }

    private String computeHmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
            SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) return null;

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (valueStart >= json.length()) return null;

        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd == -1) return null;
            return json.substring(valueStart + 1, valueEnd);
        } else {
            int valueEnd = valueStart;
            while (valueEnd < json.length()
                   && json.charAt(valueEnd) != ','
                   && json.charAt(valueEnd) != '}') {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd).trim();
        }
    }
}
