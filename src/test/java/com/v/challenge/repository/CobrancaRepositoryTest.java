package com.v.challenge.repository;

import com.v.challenge.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CobrancaRepositoryTest {

    @Autowired
    private CobrancaRepository cobrancaRepository;

    @Test
    void devePersistirCobrancaComIdGerado() {
        Cobranca cobranca = Cobranca.builder()
                .idUsuario("user-123")
                .nomeSolicitante("João Silva")
                .tipo(CobrancaTipoEnum.RECARGA)
                .metodo(CobrancaMetodoEnum.PIX)
                .status(CobrancaStatusEnum.SOLICITADA)
                .valorSolicitacao(new BigDecimal("25.50"))
                .txid("PIX123")
                .dataCriacao(LocalDateTime.now())
                .build();

        Cobranca salva = cobrancaRepository.save(cobranca);

        assertThat(salva.getId()).isNotNull();
        assertThat(salva.getIdUsuario()).isEqualTo("user-123");
        assertThat(salva.getNomeSolicitante()).isEqualTo("João Silva");
        assertThat(salva.getTipo()).isEqualTo(CobrancaTipoEnum.RECARGA);
        assertThat(salva.getMetodo()).isEqualTo(CobrancaMetodoEnum.PIX);
        assertThat(salva.getStatus()).isEqualTo(CobrancaStatusEnum.SOLICITADA);
        assertThat(salva.getValorSolicitacao()).isEqualByComparingTo(new BigDecimal("25.50"));
    }

    @Test
    void deveBuscarPorTxidMaisRecente() {
        LocalDateTime agora = LocalDateTime.now();

        Cobranca antiga = Cobranca.builder()
                .idUsuario("user-1")
                .nomeSolicitante("Maria")
                .tipo(CobrancaTipoEnum.RECARGA)
                .metodo(CobrancaMetodoEnum.PIX)
                .status(CobrancaStatusEnum.SOLICITADA)
                .txid("TXID-COMUM")
                .dataCriacao(agora.minusHours(2))
                .build();

        Cobranca recente = Cobranca.builder()
                .idUsuario("user-1")
                .nomeSolicitante("Maria")
                .tipo(CobrancaTipoEnum.RECARGA)
                .metodo(CobrancaMetodoEnum.PIX)
                .status(CobrancaStatusEnum.AGUARDANDO_PAGAMENTO)
                .txid("TXID-COMUM")
                .dataCriacao(agora)
                .build();

        cobrancaRepository.save(antiga);
        Cobranca recenteSalva = cobrancaRepository.save(recente);

        Optional<Cobranca> resultado = cobrancaRepository.findTopByTxidOrderByDataCriacaoDesc("TXID-COMUM");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(recenteSalva.getId());
        assertThat(resultado.get().getStatus()).isEqualTo(CobrancaStatusEnum.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void deveBuscarPorTransactionId() {
        Cobranca cobranca = Cobranca.builder()
                .idUsuario("user-456")
                .nomeSolicitante("Carlos")
                .tipo(CobrancaTipoEnum.ENVIO_CARTAO)
                .metodo(CobrancaMetodoEnum.CARTAO_CREDITO)
                .status(CobrancaStatusEnum.EM_PROCESSAMENTO)
                .transactionId("TXN-ABC-123")
                .dataCriacao(LocalDateTime.now())
                .build();

        Cobranca salva = cobrancaRepository.save(cobranca);

        Optional<Cobranca> resultado = cobrancaRepository.findByTransactionId("TXN-ABC-123");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(salva.getId());
        assertThat(resultado.get().getTransactionId()).isEqualTo("TXN-ABC-123");
    }

    @Test
    void deveRetornarVersaoMaisRecenteQuandoExistemFilhas() {
        LocalDateTime agora = LocalDateTime.now();

        Cobranca original = Cobranca.builder()
                .idUsuario("user-789")
                .nomeSolicitante("Ana")
                .tipo(CobrancaTipoEnum.RECARGA_TERCEIROS)
                .metodo(CobrancaMetodoEnum.PIX)
                .status(CobrancaStatusEnum.SOLICITADA)
                .dataCriacao(agora.minusHours(3))
                .build();
        Cobranca originalSalva = cobrancaRepository.save(original);

        Cobranca filha = Cobranca.builder()
                .idUsuario("user-789")
                .nomeSolicitante("Ana")
                .tipo(CobrancaTipoEnum.RECARGA_TERCEIROS)
                .metodo(CobrancaMetodoEnum.PIX)
                .status(CobrancaStatusEnum.FINALIZADA)
                .dataCriacao(agora)
                .idCobrancaOrigem(originalSalva.getId())
                .build();
        Cobranca filhaSalva = cobrancaRepository.save(filha);

        Optional<Cobranca> resultado = cobrancaRepository.findVersaoMaisRecente(originalSalva.getId());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(filhaSalva.getId());
        assertThat(resultado.get().getStatus()).isEqualTo(CobrancaStatusEnum.FINALIZADA);
    }

    @Test
    void deveRetornarOriginalQuandoNaoExistemFilhas() {
        Cobranca original = Cobranca.builder()
                .idUsuario("user-solo")
                .nomeSolicitante("Pedro")
                .tipo(CobrancaTipoEnum.RECARGA)
                .metodo(CobrancaMetodoEnum.CARTAO_CREDITO)
                .status(CobrancaStatusEnum.AGUARDANDO_PAGAMENTO)
                .dataCriacao(LocalDateTime.now())
                .build();
        Cobranca salva = cobrancaRepository.save(original);

        Optional<Cobranca> resultado = cobrancaRepository.findVersaoMaisRecente(salva.getId());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(salva.getId());
    }

    @Test
    void deveRetornarOptionalVazioQuandoTxidNaoExiste() {
        Optional<Cobranca> resultado = cobrancaRepository.findTopByTxidOrderByDataCriacaoDesc("TXID-INEXISTENTE");

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveRetornarOptionalVazioQuandoTransactionIdNaoExiste() {
        Optional<Cobranca> resultado = cobrancaRepository.findByTransactionId("TXN-INEXISTENTE");

        assertThat(resultado).isEmpty();
    }
}
