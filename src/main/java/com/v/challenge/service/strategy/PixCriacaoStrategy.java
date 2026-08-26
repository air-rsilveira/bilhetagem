package com.v.challenge.service.strategy;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.integration.PagamentoGatewayClient;
import com.v.challenge.integration.PixCriacaoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PixCriacaoStrategy implements CobrancaCriacaoStrategy {

    private final PagamentoGatewayClient gatewayClient;

    @Override
    public Cobranca executar(Cobranca cobranca) {
        PixCriacaoResponse response = gatewayClient.criarPix(
            cobranca.getValorSolicitacao(),
            cobranca.getIdUsuario()
        );

        cobranca.setTxid(response.txid());
        cobranca.setCopiaECola(response.copiaECola());
        cobranca.setDataExpiracao(response.dataExpiracao());

        return cobranca;
    }

    @Override
    public CobrancaMetodoEnum getMetodo() {
        return CobrancaMetodoEnum.PIX;
    }
}
