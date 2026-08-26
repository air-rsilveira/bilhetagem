package com.v.challenge.domain;

import lombok.Getter;

@Getter
public enum CobrancaStatusEnum {
    SOLICITADA(2),
    AGUARDANDO_PAGAMENTO(3),
    EM_PROCESSAMENTO(4),
    FINALIZADA(5),
    EXPIRADA(6),
    CANCELADA(7),
    ERRO_APROVACAO_PEDIDO(8),
    EM_REPROCESSAMENTO(9),
    ERRO_ANALISE_PENDENTE(10);

    private final int code;

    CobrancaStatusEnum(int code) {
        this.code = code;
    }

    public static CobrancaStatusEnum fromCode(int code) {
        for (CobrancaStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Código de status inválido: " + code);
    }
}
