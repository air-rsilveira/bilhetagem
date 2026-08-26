package com.v.challenge.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
public class PagamentoGatewayClient {

    public PixCriacaoResponse criarPix(BigDecimal valor, String idUsuario) {
        log.info("Criando PIX - valor: {}, usuario: {}", valor, idUsuario);
        return new PixCriacaoResponse(
            "PIX" + System.currentTimeMillis(),
            "00020126580014BR.GOV.BCB.PIX...",
            LocalDateTime.now().plusHours(2)
        );
    }

    public CartaoTransacaoResponse iniciarTransacaoCartao(BigDecimal valor, String idUsuario) {
        log.info("Iniciando transação cartão - valor: {}, usuario: {}", valor, idUsuario);
        return new CartaoTransacaoResponse(
            "TXN" + System.currentTimeMillis(),
            false,
            null,
            null
        );
    }
}
