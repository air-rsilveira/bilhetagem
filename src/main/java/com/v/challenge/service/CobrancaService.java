package com.v.challenge.service;

import com.v.challenge.domain.*;
import com.v.challenge.dto.CheckoutValidateRequestDTO;
import com.v.challenge.dto.CobrancaBasicoResponseDTO;
import com.v.challenge.dto.CobrancaCompletoResponseDTO;
import com.v.challenge.dto.CobrancaRequestDTO;
import com.v.challenge.dto.PixWebhookDTO;
import com.v.challenge.dto.PixWebhookItemDTO;
import com.v.challenge.event.CobrancaEventPublisher;
import com.v.challenge.exception.CobrancaNaoEncontradaException;
import com.v.challenge.exception.LockIndisponivelException;
import com.v.challenge.integration.CheckoutValidationClient;
import com.v.challenge.integration.CheckoutValidationResponse;
import com.v.challenge.integration.StatusConsultaExternaClient;
import com.v.challenge.lock.LockExecutor;
import com.v.challenge.repository.CobrancaRepository;
import com.v.challenge.security.UserContext;
import com.v.challenge.security.UserContextHolder;
import com.v.challenge.service.strategy.CobrancaCriacaoStrategy;
import com.v.challenge.service.strategy.CobrancaCriacaoStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CobrancaService {

    private final CobrancaRepository repository;
    private final LockExecutor lockExecutor;
    private final CobrancaCriacaoStrategyRegistry strategyRegistry;
    private final CobrancaEventPublisher eventPublisher;
    private final StatusConsultaExternaClient statusConsultaExternaClient;
    private final CheckoutValidationClient checkoutValidationClient;

    private static final ZoneId ZONE_SP = ZoneId.of("America/Sao_Paulo");

    public CobrancaBasicoResponseDTO criarCobranca(CobrancaRequestDTO request) {
        UserContext userContext = UserContextHolder.getContext();
        String lockKey = "cobrancas:" + userContext.idUsuario();

        try {
            return lockExecutor.executeWithLock(lockKey, Duration.ofSeconds(5), () -> {
                Cobranca cobranca = new Cobranca();
                cobranca.setIdUsuario(userContext.idUsuario());
                cobranca.setNomeSolicitante(userContext.getNomeCompleto());
                cobranca.setValorSolicitacao(request.valor());
                cobranca.setTipo(request.tipoEfetivo());
                cobranca.setMetodo(request.metodoEfetivo());
                cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
                cobranca.setDataCriacao(LocalDateTime.now(ZONE_SP));

                CobrancaCriacaoStrategy strategy = strategyRegistry.getStrategy(cobranca.getMetodo());
                cobranca = strategy.executar(cobranca);

                cobranca = repository.save(cobranca);

                eventPublisher.publicarCobrancaCriada(cobranca);

                return new CobrancaBasicoResponseDTO(
                    cobranca.getId(),
                    cobranca.getTxid(),
                    cobranca.getCopiaECola(),
                    cobranca.getDataExpiracao(),
                    cobranca.getTransactionId()
                );
            });
        } catch (LockIndisponivelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Erro ao criar cobrança.", ex);
        }
    }

    public CobrancaCompletoResponseDTO consultarCobranca(Long id) {
        Cobranca cobrancaOriginal = repository.findById(id)
            .orElseThrow(() -> new CobrancaNaoEncontradaException("Cobrança não encontrada"));

        Cobranca cobrancaAtual = repository.findVersaoMaisRecente(id)
            .orElse(cobrancaOriginal);

        if (cobrancaAtual.getMetodo() == CobrancaMetodoEnum.PIX &&
            isStatusConsultavel(cobrancaAtual.getStatus())) {

            CobrancaStatusEnum statusExterno = statusConsultaExternaClient
                .consultarStatus(cobrancaAtual.getTxid());

            if (statusExterno != cobrancaAtual.getStatus()) {
                CobrancaStatusEnum statusAnterior = cobrancaAtual.getStatus();

                Cobranca novaVersao = criarNovaVersao(cobrancaAtual);
                novaVersao.setStatus(statusExterno);
                novaVersao.setDataCriacao(LocalDateTime.now(ZONE_SP));

                novaVersao = repository.save(novaVersao);
                eventPublisher.publicarStatusAlterado(novaVersao, statusAnterior);

                cobrancaAtual = novaVersao;
            }
        }

        return mapearParaResponseCompleto(cobrancaAtual);
    }

    public void processarWebhookPix(PixWebhookDTO webhook) {
        if (webhook == null || webhook.pix() == null || webhook.pix().isEmpty()) {
            return;
        }

        for (PixWebhookItemDTO item : webhook.pix()) {
            processarItemWebhook(item);
        }
    }

    public void validarCheckout(String transactionId, CheckoutValidateRequestDTO request) {
        Cobranca cobranca = repository.findByTransactionId(transactionId)
            .orElseThrow(() -> new CobrancaNaoEncontradaException("Cobrança não encontrada"));

        CheckoutValidationResponse response = checkoutValidationClient.validarCheckout(
            transactionId,
            request.cavv(),
            request.xid(),
            request.eci()
        );

        CobrancaStatusEnum statusAnterior = cobranca.getStatus();

        if (response.aprovado()) {
            cobranca.setStatus(CobrancaStatusEnum.FINALIZADA);
            cobranca.setValorPago(cobranca.getValorSolicitacao());
            cobranca.setDataFinalizada(LocalDateTime.now(ZONE_SP));
        } else {
            cobranca.setStatus(CobrancaStatusEnum.ERRO_APROVACAO_PEDIDO);
        }

        cobranca.setThreeDsPayload(response.threeDsResult());

        repository.save(cobranca);
        eventPublisher.publicarStatusAlterado(cobranca, statusAnterior);
    }

    private void processarItemWebhook(PixWebhookItemDTO item) {
        if (item.txid() == null || item.txid().trim().isEmpty()) {
            return;
        }

        Optional<Cobranca> cobrancaOpt = repository.findTopByTxidOrderByDataCriacaoDesc(item.txid());
        if (cobrancaOpt.isEmpty()) {
            return;
        }

        Cobranca cobrancaAtual = cobrancaOpt.get();
        if (cobrancaAtual.getStatus() == CobrancaStatusEnum.FINALIZADA) {
            return;
        }

        CobrancaStatusEnum statusAnterior = cobrancaAtual.getStatus();

        Cobranca novaVersao = criarNovaVersao(cobrancaAtual);
        novaVersao.setStatus(CobrancaStatusEnum.FINALIZADA);
        novaVersao.setValorPago(item.valor());
        novaVersao.setDataFinalizada(LocalDateTime.now(ZONE_SP));
        novaVersao.setDataCriacao(LocalDateTime.now(ZONE_SP));

        repository.save(novaVersao);
        eventPublisher.publicarStatusAlterado(novaVersao, statusAnterior);
    }

    private boolean isStatusConsultavel(CobrancaStatusEnum status) {
        return Set.of(
            CobrancaStatusEnum.SOLICITADA,
            CobrancaStatusEnum.EXPIRADA,
            CobrancaStatusEnum.ERRO_APROVACAO_PEDIDO,
            CobrancaStatusEnum.EM_REPROCESSAMENTO,
            CobrancaStatusEnum.ERRO_ANALISE_PENDENTE
        ).contains(status);
    }

    Cobranca criarNovaVersao(Cobranca cobrancaOriginal) {
        Cobranca novaVersao = new Cobranca();
        BeanUtils.copyProperties(cobrancaOriginal, novaVersao, "id", "dataCriacao");
        novaVersao.setIdCobrancaOrigem(
            cobrancaOriginal.getIdCobrancaOrigem() != null ?
            cobrancaOriginal.getIdCobrancaOrigem() :
            cobrancaOriginal.getId()
        );
        return novaVersao;
    }

    private CobrancaCompletoResponseDTO mapearParaResponseCompleto(Cobranca cobranca) {
        return new CobrancaCompletoResponseDTO(
            cobranca.getId(),
            cobranca.getTxid(),
            cobranca.getIdUsuario(),
            cobranca.getTipo(),
            cobranca.getMetodo(),
            cobranca.getStatus(),
            cobranca.getValorSolicitacao(),
            cobranca.getValorPago(),
            cobranca.getDataCriacao(),
            cobranca.getDataExpiracao(),
            cobranca.getDataFinalizada()
        );
    }
}
