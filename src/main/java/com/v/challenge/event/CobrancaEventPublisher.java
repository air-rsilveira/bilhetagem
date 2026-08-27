package com.v.challenge.event;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaStatusEnum;

public interface CobrancaEventPublisher {
    void publicarCobrancaCriada(Cobranca cobranca);
    void publicarStatusAlterado(Cobranca cobranca, CobrancaStatusEnum statusAnterior);
}
