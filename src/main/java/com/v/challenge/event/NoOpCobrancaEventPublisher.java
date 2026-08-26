package com.v.challenge.event;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnMissingBean(KafkaTemplate.class)
public class NoOpCobrancaEventPublisher implements CobrancaEventPublisher {

    @Override
    public void publicarCobrancaCriada(Cobranca cobranca) {
        log.debug("NoOp - publicarCobrancaCriada: {}", cobranca.getId());
    }

    @Override
    public void publicarStatusAlterado(Cobranca cobranca, CobrancaStatusEnum statusAnterior) {
        log.debug("NoOp - publicarStatusAlterado: {} de {} para {}",
            cobranca.getId(), statusAnterior, cobranca.getStatus());
    }
}
