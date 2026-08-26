package com.v.challenge.dto;

import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.domain.CobrancaTipoEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CobrancaRequestDTO(
    @NotNull @Positive BigDecimal valor,
    CobrancaTipoEnum tipo,
    CobrancaMetodoEnum metodo
) {
    public CobrancaTipoEnum tipoEfetivo() {
        return tipo != null ? tipo : CobrancaTipoEnum.RECARGA;
    }

    public CobrancaMetodoEnum metodoEfetivo() {
        return metodo != null ? metodo : CobrancaMetodoEnum.PIX;
    }
}
