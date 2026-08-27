package com.v.challenge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.AfterTry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests para JwtAuthenticationFilter.
 * Validates: Requirements 2.1, 2.2, 2.3, 2.5, 2.7, 4.4
 */
class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter();

    @AfterTry
    void cleanupAfterTry() {
        UserContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterProperty
    void tearDown() {
        UserContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    // ===== Property 1: Round-trip de Claims JWT =====
    // Para qualquer conjunto válido de claims (sub, given_name, family_name, cpf),
    // se um token JWT for gerado pelo JwtTokenUtil e processado pelo filter,
    // o UserContext resultante deve conter exatamente os mesmos valores.
    // Validates: Requirements 2.1, 2.2

    @Property(tries = 100)
    void property1_roundTripDeClaimsJwt(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String sub,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String givenName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String familyName,
            @ForAll("cpfValido") String cpf
    ) throws ServletException, IOException {
        // Arrange
        String token = JwtTokenUtil.generateToken(sub, givenName, familyName, cpf, 3600);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert — the filter chain was invoked (request passed through)
        assertThat(response.getStatus()).isEqualTo(200);

        // Retrieve context that was set during filter execution
        // Since filterChain was called, we need to verify the UserContext was set properly.
        // The filter clears the context in finally block, so we verify via the filterChain being called.
        // For round-trip verification, we use a custom FilterChain that captures the context.
        UserContextHolder.clear();
        SecurityContextHolder.clearContext();

        // Re-run with capturing filter chain
        MockHttpServletRequest request2 = createRequestWithToken(token);
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        UserContext[] captured = new UserContext[1];
        FilterChain capturingChain = (req, res) -> {
            captured[0] = UserContextHolder.getContext();
        };

        filter.doFilter(request2, response2, capturingChain);

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].idUsuario()).isEqualTo(sub);
        assertThat(captured[0].givenName()).isEqualTo(givenName);
        assertThat(captured[0].familyName()).isEqualTo(familyName);
        assertThat(captured[0].cpf()).isEqualTo(cpf);
    }

    // ===== Property 2: Rejeição de Assinatura Inválida =====
    // Para qualquer token JWT válido com assinatura alterada, filter deve retornar 401.
    // Validates: Requirements 2.5

    @Property(tries = 100)
    void property2_rejeicaoDeAssinaturaInvalida(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String sub,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String givenName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String familyName,
            @ForAll("cpfValido") String cpf
    ) throws ServletException, IOException {
        // Arrange — gera token válido e corrompe a assinatura
        String validToken = JwtTokenUtil.generateToken(sub, givenName, familyName, cpf, 3600);
        String[] parts = validToken.split("\\.");
        // Altera um caractere na assinatura para invalidá-la
        String corruptedSignature = parts[2].charAt(0) == 'A'
                ? "B" + parts[2].substring(1)
                : "A" + parts[2].substring(1);
        String tamperedToken = parts[0] + "." + parts[1] + "." + corruptedSignature;

        MockHttpServletRequest request = createRequestWithToken(tamperedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ===== Property 3: Rejeição de Token Expirado =====
    // Para qualquer token com exp no passado, filter deve retornar 401.
    // Validates: Requirements 2.7

    @Property(tries = 100)
    void property3_rejeicaoDeTokenExpirado(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String sub,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String givenName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String familyName,
            @ForAll("cpfValido") String cpf
    ) throws ServletException, IOException {
        // Arrange — gera token expirado
        String expiredToken = JwtTokenUtil.generateExpiredToken(sub, givenName, familyName, cpf);

        MockHttpServletRequest request = createRequestWithToken(expiredToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ===== Property 4: Formato JWT Válido na Geração =====
    // Tokens gerados pelo JwtTokenUtil devem ter exatamente 3 partes Base64URL válidas.
    // Validates: Requirements 4.4

    @Property(tries = 100)
    void property4_formatoJwtValidoNaGeracao(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String sub,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String givenName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String familyName,
            @ForAll("cpfValido") String cpf,
            @ForAll @net.jqwik.api.constraints.LongRange(min = 60, max = 86400) long expirationSeconds
    ) {
        // Arrange & Act
        String token = JwtTokenUtil.generateToken(sub, givenName, familyName, cpf, expirationSeconds);

        // Assert — deve ter exatamente 3 partes separadas por ponto
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        // Cada parte deve ser Base64URL válida (decodificável sem erros)
        Base64.Decoder decoder = Base64.getUrlDecoder();
        for (String part : parts) {
            assertThat(part).isNotEmpty();
            // Adiciona padding se necessário para decodificação
            String padded = part;
            int mod = padded.length() % 4;
            if (mod > 0) {
                padded = padded + "=".repeat(4 - mod);
            }
            byte[] decoded = decoder.decode(padded);
            assertThat(decoded).isNotEmpty();
        }
    }

    // ===== Property 6: SecurityContext Populado com Principal Correto =====
    // Após processamento de um token válido, o SecurityContext deve conter o idUsuario como principal.
    // Validates: Requirements 2.3

    @Property(tries = 100)
    void property6_securityContextPopuladoComPrincipalCorreto(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String sub,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String givenName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String familyName,
            @ForAll("cpfValido") String cpf
    ) throws ServletException, IOException {
        // Arrange
        String token = JwtTokenUtil.generateToken(sub, givenName, familyName, cpf, 3600);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Use capturing chain to verify SecurityContext during filter execution
        String[] capturedPrincipal = new String[1];
        FilterChain capturingChain = (req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                capturedPrincipal[0] = auth.getName();
            }
        };

        // Act
        filter.doFilter(request, response, capturingChain);

        // Assert
        assertThat(capturedPrincipal[0]).isEqualTo(sub);
    }

    // ===== Testes Unitários Complementares =====

    @Test
    void deveRejeitar401QuandoSemHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/cobrancas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void deveRejeitar401QuandoTokenMalformado() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/cobrancas");
        request.addHeader("Authorization", "Bearer nao-e-um-jwt-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void deveLimparContextoAposExecucao() throws ServletException, IOException {
        String token = JwtTokenUtil.generateToken("user-1", "João", "Silva", "12345678900", 3600);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        // Após o filter, o ThreadLocal deve estar limpo
        assertThat(UserContextHolder.getContext()).isNull();
    }

    // ===== Métodos Auxiliares =====

    private MockHttpServletRequest createRequestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/cobrancas");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Provide
    Arbitrary<String> cpfValido() {
        return Arbitraries.strings().numeric().ofLength(11);
    }
}
