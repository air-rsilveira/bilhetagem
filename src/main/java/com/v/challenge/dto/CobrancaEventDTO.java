package com.v.challenge.dto;

import com.v.challenge.domain.CobrancaStatusEnum;
import java.time.LocalDateTime;

public record CobrancaEventDTO(
    Long cobrancaId,
    String idUsuario,
    CobrancaStatusEnum statusAtual,
    CobrancaStatusEnum statusAnterior,
    LocalDateTime timestamp,
    String eventoTipo
) {}
