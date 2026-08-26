package com.v.challenge.integration;

import java.time.LocalDateTime;

public record PixCriacaoResponse(
    String txid,
    String copiaECola,
    LocalDateTime dataExpiracao
) {}
