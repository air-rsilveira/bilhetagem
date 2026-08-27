package com.v.challenge.dto;

import java.time.LocalDateTime;

public record CobrancaBasicoResponseDTO(
    Long id,
    String txid,
    String copiaECola,
    LocalDateTime dataExpiracao,
    String transactionId
) {}
