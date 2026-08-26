package com.v.challenge.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class CobrancaStatusEnumTest {

    /**
     * Validates: Requirements 1.3
     * Verifica que cada valor do enum possui o código numérico correto.
     */
    @Test
    void deveRetornarCodigosCorretos() {
        assertThat(CobrancaStatusEnum.SOLICITADA.getCode()).isEqualTo(2);
        assertThat(CobrancaStatusEnum.AGUARDANDO_PAGAMENTO.getCode()).isEqualTo(3);
        assertThat(CobrancaStatusEnum.EM_PROCESSAMENTO.getCode()).isEqualTo(4);
        assertThat(CobrancaStatusEnum.FINALIZADA.getCode()).isEqualTo(5);
        assertThat(CobrancaStatusEnum.EXPIRADA.getCode()).isEqualTo(6);
        assertThat(CobrancaStatusEnum.CANCELADA.getCode()).isEqualTo(7);
        assertThat(CobrancaStatusEnum.ERRO_APROVACAO_PEDIDO.getCode()).isEqualTo(8);
        assertThat(CobrancaStatusEnum.EM_REPROCESSAMENTO.getCode()).isEqualTo(9);
        assertThat(CobrancaStatusEnum.ERRO_ANALISE_PENDENTE.getCode()).isEqualTo(10);
    }

    /**
     * Property 1: Round-trip getCode/fromCode
     * Para qualquer valor do CobrancaStatusEnum, fromCode(valor.getCode()) retorna o próprio valor.
     * Validates: Requirements 1.4, 1.5
     */
    @ParameterizedTest
    @EnumSource(CobrancaStatusEnum.class)
    void deveRetornarEnumCorretoPorCodigo(CobrancaStatusEnum status) {
        assertThat(CobrancaStatusEnum.fromCode(status.getCode())).isEqualTo(status);
    }

    /**
     * Property 2: Códigos inválidos rejeitados
     * Para qualquer inteiro fora do conjunto {2..10}, fromCode() lança IllegalArgumentException.
     * Validates: Requirements 1.6
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 11, -1, 100})
    void deveLancarExcecaoParaCodigoInvalido(int codigoInvalido) {
        assertThatThrownBy(() -> CobrancaStatusEnum.fromCode(codigoInvalido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Código de status inválido");
    }
}
