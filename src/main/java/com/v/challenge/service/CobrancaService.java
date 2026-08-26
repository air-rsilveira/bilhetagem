package com.v.challenge.service;

import com.v.challenge.domain.*;
import com.v.challenge.dto.CobrancaBasicoResponseDTO;
import com.v.challenge.dto.CobrancaRequestDTO;
import com.v.challenge.event.CobrancaEventPublisher;
import com.v.challenge.exception.LockIndisponivelException;
import com.v.challenge.lock.LockExecutor;
import com.v.challenge.repository.CobrancaRepository;
import com.v.challenge.security.UserContext;
import com.v.challenge.security.UserContextHolder;
import com.v.challenge.service.strategy.CobrancaCriacaoStrategy;
import com.v.challenge.service.strategy.CobrancaCriacaoStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CobrancaService {

    private final CobrancaRepository repository;
    private final LockExecutor lockExecutor;
    private final CobrancaCriacaoStrategyRegistry strategyRegistry;
    private final CobrancaEventPublisher eventPublisher;

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
}
