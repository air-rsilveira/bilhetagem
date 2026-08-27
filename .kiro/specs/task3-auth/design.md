# Documento de Design

## Introdução

Este documento descreve a arquitetura e implementação do filtro de autenticação JWT para o microserviço de bilhetagem. A solução utiliza uma chave simétrica HMAC-SHA256 fixa para validação de tokens, sem dependência de bibliotecas externas de JWT, utilizando apenas `javax.crypto.Mac` e `java.util.Base64` da JDK padrão.

## Arquitetura

### Visão Geral

```
┌──────────────┐     ┌─────────────────────┐     ┌──────────────────┐
│   Request    │────▶│ JwtAuthFilter       │────▶│ Controller       │
│  (Bearer)    │     │  - valida token     │     │  (acessa         │
│              │     │  - popula contexto  │     │   UserContext)   │
└──────────────┘     └─────────────────────┘     └──────────────────┘
                              │                           │
                              ▼                           ▼
                     ┌─────────────────┐        ┌────────────────┐
                     │ SecurityContext  │        │ UserContext     │
                     │ (Spring)         │        │ Holder          │
                     └─────────────────┘        │ (ThreadLocal)   │
                                                └────────────────┘
```

### Fluxo de Autenticação

1. Requisição chega ao `JwtAuthenticationFilter`
2. Filter verifica se o endpoint é público (actuator, webhook) — se sim, passa direto
3. Extrai token do header `Authorization: Bearer <token>`
4. Decodifica header e payload do JWT (Base64URL)
5. Valida assinatura HMAC-SHA256 com a chave simétrica
6. Valida expiração (claim `exp`)
7. Extrai claims e popula `UserContextHolder` e `SecurityContext`
8. Passa para o próximo filtro na chain
9. No bloco `finally`, limpa o `UserContextHolder`

## Componentes

### 1. UserContext (Record)

**Arquivo:** `src/main/java/com/v/challenge/security/UserContext.java`

```java
package com.v.challenge.security;

public record UserContext(
    String idUsuario,
    String givenName,
    String familyName,
    String cpf
) {
    public String getNomeCompleto() {
        return givenName + " " + familyName;
    }
}
```

### 2. UserContextHolder

**Arquivo:** `src/main/java/com/v/challenge/security/UserContextHolder.java`

```java
package com.v.challenge.security;

public class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(UserContext context) {
        CONTEXT.set(context);
    }

    public static UserContext getContext() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
```

### 3. JwtAuthenticationFilter

**Arquivo:** `src/main/java/com/v/challenge/security/JwtAuthenticationFilter.java`

```java
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
```

### 4. SecurityConfig (Atualizado)

**Arquivo:** `src/main/java/com/v/challenge/security/SecurityConfig.java`

```java
package com.v.challenge.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/cobrancas/webhook/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### 5. JwtTokenUtil (Utilitário de Teste)

**Arquivo:** `src/test/java/com/v/challenge/security/JwtTokenUtil.java`

```java
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
```

## Modelo de Dados

### Claims do JWT

| Claim | Tipo | Mapeamento UserContext | Obrigatório |
|-------|------|----------------------|-------------|
| sub | String | idUsuario | Sim |
| given_name | String | givenName | Não |
| family_name | String | familyName | Não |
| cpf | String | cpf | Não |
| iat | Long (epoch) | - | Não |
| exp | Long (epoch) | - | Sim (para validação) |

### Formato do Token

```
Base64URL(header) . Base64URL(payload) . Base64URL(HMAC-SHA256(header.payload, secret))
```

Header fixo:
```json
{"alg":"HS256","typ":"JWT"}
```

## Tratamento de Erros

| Cenário | Comportamento | HTTP Status |
|---------|--------------|-------------|
| Sem header Authorization | Retorna imediatamente | 401 |
| Header sem prefixo "Bearer " | Retorna imediatamente | 401 |
| Token com menos de 3 partes | Retorna imediatamente | 401 |
| Assinatura inválida | Retorna imediatamente | 401 |
| Payload não é JSON válido | Retorna imediatamente | 401 |
| Claim sub ausente | Retorna imediatamente | 401 |
| Token expirado (exp < now) | Retorna imediatamente | 401 |
| Exceção inesperada no parsing | Retorna imediatamente | 401 |

## Decisões de Design

1. **Sem biblioteca JWT externa**: Implementação manual com `javax.crypto.Mac` e `java.util.Base64` para manter dependências mínimas
2. **Parse JSON manual**: Extração de valores JSON sem dependência de Jackson no filter (evita acoplamento)
3. **shouldNotFilter()**: Endpoints públicos são excluídos via override do método, evitando processamento desnecessário
4. **ThreadLocal cleanup no finally**: Garante que o contexto é limpo mesmo em caso de exceção, evitando vazamento entre requisições em thread pools
5. **Chave simétrica fixa**: Implementação mock para o desafio técnico — em produção seria substituída por validação com JWKs ou chave rotacionada

## Correctness Properties

*Uma propriedade é uma característica ou comportamento que deve ser verdadeiro em todas as execuções válidas do sistema — essencialmente, uma declaração formal sobre o que o sistema deve fazer.*

### Property 1: Round-trip de Claims JWT

*Para qualquer* conjunto válido de claims (sub, given_name, family_name, cpf), se um token JWT for gerado pelo JwtTokenUtil com essas claims e processado pelo JwtAuthenticationFilter, o UserContext resultante deve conter exatamente os mesmos valores das claims originais.

**Validates: Requirements 2.1, 2.2, 4.1**

### Property 2: Rejeição de Assinatura Inválida

*Para qualquer* token JWT válido, se sua assinatura for alterada (qualquer byte modificado), o JwtAuthenticationFilter deve rejeitar o token e retornar 401.

**Validates: Requirements 2.5**

### Property 3: Rejeição de Token Expirado

*Para qualquer* token JWT com claim exp anterior ao timestamp atual, o JwtAuthenticationFilter deve rejeitar o token e retornar 401.

**Validates: Requirements 2.7**

### Property 4: Formato JWT Válido na Geração

*Para qualquer* conjunto de claims passado ao JwtTokenUtil, o token gerado deve conter exatamente 3 partes separadas por ponto, onde cada parte é uma string Base64URL válida.

**Validates: Requirements 4.4**

### Property 5: Concatenação de Nome Completo

*Para quaisquer* strings givenName e familyName, UserContext.getNomeCompleto() deve retornar exatamente givenName + " " + familyName.

**Validates: Requirements 1.2**

### Property 6: SecurityContext Populado com Principal Correto

*Para qualquer* token JWT válido com claim sub, após processamento pelo JwtAuthenticationFilter, o SecurityContext deve conter uma Authentication cujo getName() retorna exatamente o valor da claim sub.

**Validates: Requirements 2.3**
