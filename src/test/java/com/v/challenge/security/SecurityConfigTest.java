package com.v.challenge.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealthDeveSerAcessivelSemToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void webhookDeveSerAcessivelSemToken() throws Exception {
        // POST to webhook endpoint — should not require auth.
        // Endpoint is public and accepts an empty payload, returning 200 (not 401).
        mockMvc.perform(post("/api/v1/cobrancas/webhook/pix")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    void endpointProtegidoSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(get("/api/v1/cobrancas/1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoComTokenValidoDevePassar() throws Exception {
        String token = JwtTokenUtil.generateToken("user-123", "João", "Silva", "12345678900", 3600);

        // Should pass authentication; returns 404 because no charge with id=1 exists (not 401)
        mockMvc.perform(get("/api/v1/cobrancas/1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void endpointProtegidoComTokenExpiradoDeveRetornar401() throws Exception {
        String expiredToken = JwtTokenUtil.generateExpiredToken("user-123", "João", "Silva", "12345678900");

        mockMvc.perform(get("/api/v1/cobrancas/1")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());
    }
}
