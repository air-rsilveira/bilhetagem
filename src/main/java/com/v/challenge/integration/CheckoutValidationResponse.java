package com.v.challenge.integration;

public record CheckoutValidationResponse(
    boolean aprovado,
    String resultado,
    String threeDsResult
) {}
