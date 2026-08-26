package com.v.challenge.dto;

import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.domain.CobrancaStatusEnum;
import com.v.challenge.domain.CobrancaTipoEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CobrancaCompletoResponseDTO(
    Long id,
    String txid,
    String idUsuario,
    CobrancaTipoEnum tipo,
    CobrancaMetodoEnum metodo,
    CobrancaStatusEnum status,
    BigDecimal valorSolicitado,
    BigDecimal valorPago,
    LocalDateTime dataCriacao,
    LocalDateTime dataExpiracao,
    LocalDateTime dataFinalizada
) {}
