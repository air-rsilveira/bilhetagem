package com.v.challenge.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CheckoutValidationClient {

    public CheckoutValidationResponse validarCheckout(
            String transactionId, String cavv, String xid, String eci) {
        log.info("Validando checkout 3DS - transactionId: {}", transactionId);
        return new CheckoutValidationResponse(true, "APROVADO", "Y");
    }
}
