package com.v.challenge.service.strategy;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaMetodoEnum;

public interface CobrancaCriacaoStrategy {
    Cobranca executar(Cobranca cobranca);
    CobrancaMetodoEnum getMetodo();
}
