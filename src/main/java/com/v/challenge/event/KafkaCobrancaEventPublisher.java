package com.v.challenge.event;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaStatusEnum;
import com.v.challenge.dto.CobrancaEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(KafkaTemplate.class)
public class KafkaCobrancaEventPublisher implements CobrancaEventPublisher {

    private final KafkaTemplate<String, CobrancaEventDTO> kafkaTemplate;
    private static final String TOPIC = "cobrancas.status-alterado";

    @Override
    public void publicarCobrancaCriada(Cobranca cobranca) {
        CobrancaEventDTO event = new CobrancaEventDTO(
            cobranca.getId(),
            cobranca.getIdUsuario(),
            cobranca.getStatus(),
            null,
            LocalDateTime.now(),
            "CRIADA"
        );
        log.info("Publicando evento CRIADA - cobrancaId: {}, usuario: {}", cobranca.getId(), cobranca.getIdUsuario());
        kafkaTemplate.send(TOPIC, cobranca.getIdUsuario(), event);
    }

    @Override
    public void publicarStatusAlterado(Cobranca cobranca, CobrancaStatusEnum statusAnterior) {
        CobrancaEventDTO event = new CobrancaEventDTO(
            cobranca.getId(),
            cobranca.getIdUsuario(),
            cobranca.getStatus(),
            statusAnterior,
            LocalDateTime.now(),
            "STATUS_ALTERADO"
        );
        log.info("Publicando evento STATUS_ALTERADO - cobrancaId: {}, de {} para {}",
            cobranca.getId(), statusAnterior, cobranca.getStatus());
        kafkaTemplate.send(TOPIC, cobranca.getIdUsuario(), event);
    }
}
