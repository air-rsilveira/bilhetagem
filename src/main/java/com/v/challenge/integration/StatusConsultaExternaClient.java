package com.v.challenge.integration;

import com.v.challenge.domain.CobrancaStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StatusConsultaExternaClient {

    public CobrancaStatusEnum consultarStatus(String txid) {
        log.info("Consultando status externo - txid: {}", txid);
        return CobrancaStatusEnum.AGUARDANDO_PAGAMENTO;
    }
}
