package com.v.challenge.service.strategy;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.domain.CobrancaTipoEnum;
import com.v.challenge.domain.CobrancaStatusEnum;
import com.v.challenge.integration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyTest {

    @Mock
    private PagamentoGatewayClient gatewayClient;

    @InjectMocks
    private PixCriacaoStrategy pixStrategy;

    @InjectMocks
    private CartaoCriacaoStrategy cartaoStrategy;

    @Test
    void pixStrategy_devePreencherTxidCopiaEColaEDataExpiracao() {
        Cobranca cobranca = Cobranca.builder()
            .idUsuario("user-1")
            .nomeSolicitante("João")
            .valorSolicitacao(new BigDecimal("25.50"))
            .metodo(CobrancaMetodoEnum.PIX)
            .build();

        LocalDateTime expiracao = LocalDateTime.now().plusHours(2);
        when(gatewayClient.criarPix(any(), eq("user-1")))
            .thenReturn(new PixCriacaoResponse("PIX123", "copia-cola-value", expiracao));

        Cobranca resultado = pixStrategy.executar(cobranca);

        assertThat(resultado.getTxid()).isEqualTo("PIX123");
        assertThat(resultado.getCopiaECola()).isEqualTo("copia-cola-value");
        assertThat(resultado.getDataExpiracao()).isEqualTo(expiracao);
    }

    @Test
    void cartaoStrategy_devePreencherTransactionId() {
        Cobranca cobranca = Cobranca.builder()
            .idUsuario("user-2")
            .nomeSolicitante("Maria")
            .valorSolicitacao(new BigDecimal("100.00"))
            .metodo(CobrancaMetodoEnum.CARTAO_CREDITO)
            .build();

        when(gatewayClient.iniciarTransacaoCartao(any(), eq("user-2")))
            .thenReturn(new CartaoTransacaoResponse("TXN456", false, null, null));

        Cobranca resultado = cartaoStrategy.executar(cobranca);

        assertThat(resultado.getTransactionId()).isEqualTo("TXN456");
        assertThat(resultado.getAcsUrl()).isNull();
    }

    @Test
    void cartaoStrategy_devePreencherCampos3DSQuandoRequired() {
        Cobranca cobranca = Cobranca.builder()
            .idUsuario("user-3")
            .nomeSolicitante("Pedro")
            .valorSolicitacao(new BigDecimal("500.00"))
            .metodo(CobrancaMetodoEnum.CARTAO_CREDITO)
            .build();

        when(gatewayClient.iniciarTransacaoCartao(any(), eq("user-3")))
            .thenReturn(new CartaoTransacaoResponse("TXN789", true, "https://acs.com", "payload-3ds"));

        Cobranca resultado = cartaoStrategy.executar(cobranca);

        assertThat(resultado.getTransactionId()).isEqualTo("TXN789");
        assertThat(resultado.getAcsUrl()).isEqualTo("https://acs.com");
        assertThat(resultado.getThreeDsPayload()).isEqualTo("payload-3ds");
    }

    @Test
    void registry_deveResolverStrategyCorretaPorMetodo() {
        PixCriacaoStrategy pix = new PixCriacaoStrategy(gatewayClient);
        CartaoCriacaoStrategy cartao = new CartaoCriacaoStrategy(gatewayClient);

        CobrancaCriacaoStrategyRegistry registry = new CobrancaCriacaoStrategyRegistry(List.of(pix, cartao));

        assertThat(registry.getStrategy(CobrancaMetodoEnum.PIX)).isSameAs(pix);
        assertThat(registry.getStrategy(CobrancaMetodoEnum.CARTAO_CREDITO)).isSameAs(cartao);
    }

    @Test
    void registry_deveLancarExcecaoParaMetodoNaoRegistrado() {
        CobrancaCriacaoStrategyRegistry registry = new CobrancaCriacaoStrategyRegistry(List.of());

        assertThatThrownBy(() -> registry.getStrategy(CobrancaMetodoEnum.PIX))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Strategy não encontrada");
    }

    @Test
    void pixStrategy_deveRetornarMetodoPIX() {
        assertThat(pixStrategy.getMetodo()).isEqualTo(CobrancaMetodoEnum.PIX);
    }

    @Test
    void cartaoStrategy_deveRetornarMetodoCARTAO() {
        assertThat(cartaoStrategy.getMetodo()).isEqualTo(CobrancaMetodoEnum.CARTAO_CREDITO);
    }
}
