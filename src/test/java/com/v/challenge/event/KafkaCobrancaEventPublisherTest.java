package com.v.challenge.event;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.domain.CobrancaStatusEnum;
import com.v.challenge.domain.CobrancaTipoEnum;
import com.v.challenge.dto.CobrancaEventDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaCobrancaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, CobrancaEventDTO> kafkaTemplate;

    @InjectMocks
    private KafkaCobrancaEventPublisher publisher;

    @Test
    void publicarCobrancaCriada_deveEnviarEventoComTipoCRIADA() {
        Cobranca cobranca = Cobranca.builder()
            .id(1L)
            .idUsuario("user-123")
            .nomeSolicitante("João Silva")
            .tipo(CobrancaTipoEnum.RECARGA)
            .metodo(CobrancaMetodoEnum.PIX)
            .status(CobrancaStatusEnum.SOLICITADA)
            .valorSolicitacao(new BigDecimal("25.50"))
            .dataCriacao(LocalDateTime.now())
            .build();

        publisher.publicarCobrancaCriada(cobranca);

        ArgumentCaptor<CobrancaEventDTO> eventCaptor = ArgumentCaptor.forClass(CobrancaEventDTO.class);
        verify(kafkaTemplate).send(eq("cobrancas.status-alterado"), eq("user-123"), eventCaptor.capture());

        CobrancaEventDTO event = eventCaptor.getValue();
        assertThat(event.cobrancaId()).isEqualTo(1L);
        assertThat(event.idUsuario()).isEqualTo("user-123");
        assertThat(event.statusAtual()).isEqualTo(CobrancaStatusEnum.SOLICITADA);
        assertThat(event.statusAnterior()).isNull();
        assertThat(event.eventoTipo()).isEqualTo("CRIADA");
    }

    @Test
    void publicarStatusAlterado_deveEnviarEventoComStatusAnterior() {
        Cobranca cobranca = Cobranca.builder()
            .id(2L)
            .idUsuario("user-456")
            .nomeSolicitante("Maria Santos")
            .tipo(CobrancaTipoEnum.RECARGA)
            .metodo(CobrancaMetodoEnum.PIX)
            .status(CobrancaStatusEnum.FINALIZADA)
            .valorSolicitacao(new BigDecimal("50.00"))
            .dataCriacao(LocalDateTime.now())
            .build();

        publisher.publicarStatusAlterado(cobranca, CobrancaStatusEnum.SOLICITADA);

        ArgumentCaptor<CobrancaEventDTO> eventCaptor = ArgumentCaptor.forClass(CobrancaEventDTO.class);
        verify(kafkaTemplate).send(eq("cobrancas.status-alterado"), eq("user-456"), eventCaptor.capture());

        CobrancaEventDTO event = eventCaptor.getValue();
        assertThat(event.cobrancaId()).isEqualTo(2L);
        assertThat(event.idUsuario()).isEqualTo("user-456");
        assertThat(event.statusAtual()).isEqualTo(CobrancaStatusEnum.FINALIZADA);
        assertThat(event.statusAnterior()).isEqualTo(CobrancaStatusEnum.SOLICITADA);
        assertThat(event.eventoTipo()).isEqualTo("STATUS_ALTERADO");
    }

    @Test
    void publicarCobrancaCriada_deveUsarIdUsuarioComoKey() {
        Cobranca cobranca = Cobranca.builder()
            .id(3L)
            .idUsuario("partition-key-user")
            .nomeSolicitante("Test")
            .status(CobrancaStatusEnum.SOLICITADA)
            .dataCriacao(LocalDateTime.now())
            .build();

        publisher.publicarCobrancaCriada(cobranca);

        verify(kafkaTemplate).send(eq("cobrancas.status-alterado"), eq("partition-key-user"), org.mockito.ArgumentMatchers.any());
    }
}
