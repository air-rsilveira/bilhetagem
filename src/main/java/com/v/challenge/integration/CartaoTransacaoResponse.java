package com.v.challenge.integration;

public record CartaoTransacaoResponse(
    String transactionId,
    boolean requires3ds,
    String acsUrl,
    String threeDsPayload
) {}
