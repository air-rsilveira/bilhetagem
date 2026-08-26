package com.v.challenge.service;

import com.v.challenge.domain.*;
import com.v.challenge.dto.CheckoutValidateRequestDTO;
import com.v.challenge.dto.CobrancaBasicoResponseDTO;
import com.v.challenge.dto.CobrancaRequestDTO;
import com.v.challenge.dto.PixWebhookDTO;
import com.v.challenge.dto.PixWebhookItemDTO;
import com.v.challenge.event.CobrancaEventPublisher;
import com.v.challenge.exception.CobrancaNaoEncontradaException;
import com.v.challenge.exception.LockIndisponivelException;
import com.v.challenge.integration.CheckoutValidationClient;
import com.v.challenge.integration.CheckoutValidationResponse;
import com.v.challenge.integration.PagamentoGatewayClient;
import com.v.challenge.integration.PixCriacaoResponse;
import com.v.challenge.integration.StatusConsultaExternaClient;
import com.v.challenge.lock.LockExecutor;
import com.v.challenge.lock.LockService;
import com.v.challenge.repository.CobrancaRepository;
import com.v.challenge.security.UserContext;
import com.v.challenge.security.UserContextHolder;
import com.v.challenge.service.strategy.CobrancaCriacaoStrategyRegistry;
import com.v.challenge.service.strategy.PixCriacaoStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CobrancaServiceTest {

    @Mock
    private CobrancaRepository repository;

    @Mock
    private LockService lockService;

    @Mock
    private CobrancaEventPublisher eventPublisher;

    @Mock
    private PagamentoGatewayClient gatewayClient;

    @Mock
    private StatusConsultaExternaClient statusConsultaExternaClient;

    @Mock
    private CheckoutValidationClient checkoutValidationClient;

    private CobrancaService cobrancaService;

    @BeforeEach
    void setUp() {
        LockExecutor lockExecutor = new LockExecutor(lockService);

        PixCriacaoStrategy pixStrategy = new PixCriacaoStrategy(gatewayClient);
        CobrancaCriacaoStrategyRegistry registry = new CobrancaCriacaoStrategyRegistry(List.of(pixStrategy));

        cobrancaService = new CobrancaService(repository, lockExecutor, registry, eventPublisher, statusConsultaExternaClient, checkoutValidationClient);

        UserContextHolder.setContext(new UserContext("user-123", "João", "Silva", "12345678901"));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    /**
     * TESTE OBRIGATÓRIO #1: criarCobranca sucesso PIX
     * Cria cobrança com txid, copiaECola e dataExpiracao preenchidos.
     */
    @Test
    void teste1_criarCobrancaSucessoPix() {
        // Arrange
        when(lockService.tryLock(eq("cobrancas:user-123"), eq(Duration.ofSeconds(5)))).thenReturn(true);

        LocalDateTime expiracao = LocalDateTime.now().plusHours(2);
        when(gatewayClient.criarPix(any(), eq("user-123")))
            .thenReturn(new PixCriacaoResponse("PIX123456", "copia-e-cola-pix", expiracao));

        when(repository.save(any(Cobranca.class))).thenAnswer(invocation -> {
            Cobranca c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        CobrancaRequestDTO request = new CobrancaRequestDTO(new BigDecimal("25.50"), null, null);

        // Act
        CobrancaBasicoResponseDTO response = cobrancaService.criarCobranca(request);

        // Assert
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.txid()).isEqualTo("PIX123456");
        assertThat(response.copiaECola()).isEqualTo("copia-e-cola-pix");
        assertThat(response.dataExpiracao()).isEqualTo(expiracao);
        assertThat(response.transactionId()).isNull();

        // Verify event published
        verify(eventPublisher).publicarCobrancaCriada(any(Cobranca.class));
        // Verify lock released
        verify(lockService).unlock("cobrancas:user-123");
    }

    /**
     * TESTE OBRIGATÓRIO #2: criarCobranca com lock indisponível
     * Lança LockIndisponivelException com mensagem correta.
     */
    @Test
    void teste2_criarCobrancaComLockIndisponivel() {
        // Arrange
        when(lockService.tryLock(eq("cobrancas:user-123"), eq(Duration.ofSeconds(5)))).thenReturn(false);

        CobrancaRequestDTO request = new CobrancaRequestDTO(new BigDecimal("25.50"), null, null);

        // Act & Assert
        assertThatThrownBy(() -> cobrancaService.criarCobranca(request))
            .isInstanceOf(LockIndisponivelException.class)
            .hasMessage("Geração de cobrança em andamento.");

        // Verify nothing was saved or published
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publicarCobrancaCriada(any());
    }

    /**
     * TESTE OBRIGATÓRIO #3: criarCobranca com exceção inesperada
     * Mapeia para RuntimeException "Erro ao criar cobrança."
     */
    @Test
    void teste3_criarCobrancaComExcecaoInesperada() {
        // Arrange
        when(lockService.tryLock(eq("cobrancas:user-123"), eq(Duration.ofSeconds(5)))).thenReturn(true);
        when(gatewayClient.criarPix(any(), any()))
            .thenThrow(new RuntimeException("Erro de conexão com gateway"));

        CobrancaRequestDTO request = new CobrancaRequestDTO(new BigDecimal("25.50"), null, null);

        // Act & Assert
        assertThatThrownBy(() -> cobrancaService.criarCobranca(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Erro ao criar cobrança.");

        // Verify lock was released even with exception (via LockExecutor)
        verify(lockService).unlock("cobrancas:user-123");
    }

    @Test
    void criarCobranca_deveAplicarDefaultsQuandoTipoEMetodoNulos() {
        // Arrange
        when(lockService.tryLock(any(), any())).thenReturn(true);
        when(gatewayClient.criarPix(any(), any()))
            .thenReturn(new PixCriacaoResponse("PIX-DEFAULT", "cola", LocalDateTime.now().plusHours(1)));
        when(repository.save(any(Cobranca.class))).thenAnswer(i -> {
            Cobranca c = i.getArgument(0);
            c.setId(10L);
            return c;
        });

        CobrancaRequestDTO request = new CobrancaRequestDTO(new BigDecimal("50.00"), null, null);

        // Act
        cobrancaService.criarCobranca(request);

        // Assert - verify save was called with correct defaults
        verify(repository).save(argThat(cobranca ->
            cobranca.getTipo() == CobrancaTipoEnum.RECARGA &&
            cobranca.getMetodo() == CobrancaMetodoEnum.PIX &&
            cobranca.getStatus() == CobrancaStatusEnum.SOLICITADA &&
            cobranca.getNomeSolicitante().equals("João Silva")
        ));
    }

    @Test
    void consultarCobranca_deveRetornarCobrancaExistente() {
        Cobranca cobranca = Cobranca.builder()
            .id(1L)
            .idUsuario("user-123")
            .nomeSolicitante("João Silva")
            .tipo(CobrancaTipoEnum.RECARGA)
            .metodo(CobrancaMetodoEnum.PIX)
            .status(CobrancaStatusEnum.FINALIZADA)
            .valorSolicitacao(new BigDecimal("25.50"))
            .dataCriacao(LocalDateTime.now())
            .txid("PIX123")
            .build();

        when(repository.findById(1L)).thenReturn(java.util.Optional.of(cobranca));
        when(repository.findVersaoMaisRecente(1L)).thenReturn(java.util.Optional.of(cobranca));

        var response = cobrancaService.consultarCobranca(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(CobrancaStatusEnum.FINALIZADA);
        assertThat(response.idUsuario()).isEqualTo("user-123");
    }

    @Test
    void consultarCobranca_deveLancar404QuandoNaoEncontrada() {
        when(repository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> cobrancaService.consultarCobranca(999L))
            .isInstanceOf(CobrancaNaoEncontradaException.class);
    }

    @Test
    void consultarCobranca_deveCriarNovaVersaoQuandoStatusExternoMudou() {
        Cobranca cobranca = Cobranca.builder()
            .id(1L)
            .idUsuario("user-123")
            .nomeSolicitante("João Silva")
            .tipo(CobrancaTipoEnum.RECARGA)
            .metodo(CobrancaMetodoEnum.PIX)
            .status(CobrancaStatusEnum.SOLICITADA)
            .valorSolicitacao(new BigDecimal("25.50"))
            .dataCriacao(LocalDateTime.now())
            .txid("PIX-STATUS")
            .build();

        when(repository.findById(1L)).thenReturn(java.util.Optional.of(cobranca));
        when(repository.findVersaoMaisRecente(1L)).thenReturn(java.util.Optional.of(cobranca));
        when(statusConsultaExternaClient.consultarStatus("PIX-STATUS"))
            .thenReturn(CobrancaStatusEnum.FINALIZADA);
        when(repository.save(any(Cobranca.class))).thenAnswer(i -> {
            Cobranca c = i.getArgument(0);
            c.setId(2L);
            return c;
        });

        var response = cobrancaService.consultarCobranca(1L);

        assertThat(response.status()).isEqualTo(CobrancaStatusEnum.FINALIZADA);
        verify(repository).save(any(Cobranca.class));
        verify(eventPublisher).publicarStatusAlterado(any(), eq(CobrancaStatusEnum.SOLICITADA));
    }

    /**
     * TESTE OBRIGATÓRIO #4: processarNotificacaoWebhookPix finalizando cobrança pendente
     * Cria nova versão com status FINALIZADA.
     */
    @Test
    void teste4_processarWebhookPixFinalizandoCobrancaPendente() {
        // Arrange
        Cobranca cobrancaPendente = Cobranca.builder()
            .id(1L)
            .idUsuario("user-123")
            .nomeSolicitante("João Silva")
            .tipo(CobrancaTipoEnum.RECARGA)
            .metodo(CobrancaMetodoEnum.PIX)
            .status(CobrancaStatusEnum.SOLICITADA)
            .valorSolicitacao(new BigDecimal("25.50"))
            .txid("PIX-WEBHOOK-TEST")
            .dataCriacao(LocalDateTime.now())
            .build();

        when(repository.findTopByTxidOrderByDataCriacaoDesc("PIX-WEBHOOK-TEST"))
            .thenReturn(java.util.Optional.of(cobrancaPendente));
        when(repository.save(any(Cobranca.class))).thenAnswer(i -> {
            Cobranca c = i.getArgument(0);
            c.setId(2L);
            return c;
        });

        PixWebhookDTO webhook = new PixWebhookDTO(List.of(
            new PixWebhookItemDTO("PIX-WEBHOOK-TEST", LocalDateTime.now(), new BigDecimal("25.50"))
        ));

        // Act
        cobrancaService.processarWebhookPix(webhook);

        // Assert
        verify(repository).save(argThat(cobranca ->
            cobranca.getStatus() == CobrancaStatusEnum.FINALIZADA &&
            cobranca.getValorPago().compareTo(new BigDecimal("25.50")) == 0 &&
            cobranca.getDataFinalizada() != null &&
            cobranca.getIdCobrancaOrigem().equals(1L)
        ));
        verify(eventPublisher).publicarStatusAlterado(any(), eq(CobrancaStatusEnum.SOLICITADA));
    }

    /**
     * TESTE OBRIGATÓRIO #5: processarNotificacaoWebhookPix ignorando cobrança já finalizada
     * Não cria nova versão.
     */
    @Test
    void teste5_processarWebhookPixIgnorandoCobrancaJaFinalizada() {
        // Arrange
        Cobranca cobrancaFinalizada = Cobranca.builder()
            .id(1L)
            .idUsuario("user-123")
            .nomeSolicitante("João Silva")
            .tipo(CobrancaTipoEnum.RECARGA)
            .metodo(CobrancaMetodoEnum.PIX)
            .status(CobrancaStatusEnum.FINALIZADA)
            .valorSolicitacao(new BigDecimal("25.50"))
            .valorPago(new BigDecimal("25.50"))
            .txid("PIX-JA-FINALIZADA")
            .dataCriacao(LocalDateTime.now())
            .build();

        when(repository.findTopByTxidOrderByDataCriacaoDesc("PIX-JA-FINALIZADA"))
            .thenReturn(java.util.Optional.of(cobrancaFinalizada));

        PixWebhookDTO webhook = new PixWebhookDTO(List.of(
            new PixWebhookItemDTO("PIX-JA-FINALIZADA", LocalDateTime.now(), new BigDecimal("25.50"))
        ));

        // Act
        cobrancaService.processarWebhookPix(webhook);

        // Assert - nenhuma nova versão criada
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publicarStatusAlterado(any(), any());
    }

    @Test
    void processarWebhookPix_deveIgnorarPayloadVazio() {
        cobrancaService.processarWebhookPix(null);
        cobrancaService.processarWebhookPix(new PixWebhookDTO(null));
        cobrancaService.processarWebhookPix(new PixWebhookDTO(List.of()));

        verify(repository, never()).findTopByTxidOrderByDataCriacaoDesc(any());
    }

    /**
     * TESTE OBRIGATÓRIO #6: validarCheckout atualizando cobrança existente
     * Chama client e atualiza status conforme aprovação.
     */
    @Test
    void teste6_validarCheckoutAtualizandoCobrancaExistente() {
        // Arrange
        Cobranca cobranca = Cobranca.builder()
            .id(1L)
            .idUsuario("user-123")
            .nomeSolicitante("João Silva")
            .tipo(CobrancaTipoEnum.ENVIO_CARTAO)
            .metodo(CobrancaMetodoEnum.CARTAO_CREDITO)
            .status(CobrancaStatusEnum.SOLICITADA)
            .valorSolicitacao(new BigDecimal("100.00"))
            .transactionId("TXN-CHECKOUT-TEST")
            .dataCriacao(LocalDateTime.now())
            .build();

        when(repository.findByTransactionId("TXN-CHECKOUT-TEST"))
            .thenReturn(java.util.Optional.of(cobranca));
        when(checkoutValidationClient.validarCheckout("TXN-CHECKOUT-TEST", "CAVV123", "XID456", "05"))
            .thenReturn(new CheckoutValidationResponse(true, "APPROVED", "3DS-result-data"));
        when(repository.save(any(Cobranca.class))).thenAnswer(i -> i.getArgument(0));

        CheckoutValidateRequestDTO request = new CheckoutValidateRequestDTO("CAVV123", "XID456", "05");

        // Act
        cobrancaService.validarCheckout("TXN-CHECKOUT-TEST", request);

        // Assert
        verify(repository).save(argThat(c ->
            c.getStatus() == CobrancaStatusEnum.FINALIZADA &&
            c.getValorPago().compareTo(new BigDecimal("100.00")) == 0 &&
            c.getDataFinalizada() != null &&
            c.getThreeDsPayload().equals("3DS-result-data")
        ));
        verify(eventPublisher).publicarStatusAlterado(any(), eq(CobrancaStatusEnum.SOLICITADA));
    }

    @Test
    void validarCheckout_deveLancar404QuandoTransactionIdNaoExiste() {
        when(repository.findByTransactionId("TXN-INEXISTENTE"))
            .thenReturn(java.util.Optional.empty());

        CheckoutValidateRequestDTO request = new CheckoutValidateRequestDTO("CAVV", "XID", "05");

        assertThatThrownBy(() -> cobrancaService.validarCheckout("TXN-INEXISTENTE", request))
            .isInstanceOf(CobrancaNaoEncontradaException.class);
    }
}
