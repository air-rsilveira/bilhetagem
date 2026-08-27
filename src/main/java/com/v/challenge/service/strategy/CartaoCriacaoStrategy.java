package com.v.challenge.service.strategy;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.integration.CartaoTransacaoResponse;
import com.v.challenge.integration.PagamentoGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartaoCriacaoStrategy implements CobrancaCriacaoStrategy {

    private final PagamentoGatewayClient gatewayClient;

    @Override
    public Cobranca executar(Cobranca cobranca) {
        CartaoTransacaoResponse response = gatewayClient.iniciarTransacaoCartao(
            cobranca.getValorSolicitacao(),
            cobranca.getIdUsuario()
        );

        cobranca.setTransactionId(response.transactionId());

        if (response.requires3ds()) {
            cobranca.setAcsUrl(response.acsUrl());
            cobranca.setThreeDsPayload(response.threeDsPayload());
        }

        return cobranca;
    }

    @Override
    public CobrancaMetodoEnum getMetodo() {
        return CobrancaMetodoEnum.CARTAO_CREDITO;
    }
}
