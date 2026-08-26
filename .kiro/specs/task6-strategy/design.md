# Documento de Design

## Introdução

Este documento detalha a arquitetura e o design do padrão Strategy para criação de cobranças por método de pagamento. O padrão encapsula a lógica específica de cada método (PIX, Cartão de Crédito) em classes dedicadas, permitindo extensibilidade e separação de responsabilidades.

## Arquitetura

```
┌─────────────────────────────────┐
│        CobrancaService          │
└──────────────┬──────────────────┘
               │ usa
               ▼
┌─────────────────────────────────┐
│  CobrancaCriacaoStrategyRegistry│
│  Map<MetodoEnum, Strategy>      │
└──────────────┬──────────────────┘
               │ resolve
        ┌──────┴──────┐
        ▼             ▼
┌──────────────┐ ┌──────────────────┐
│PixCriacao    │ │CartaoCriacao     │
│Strategy      │ │Strategy          │
└──────┬───────┘ └────────┬─────────┘
       │                  │
       ▼                  ▼
┌─────────────────────────────────┐
│     PagamentoGatewayClient      │
│     (mock)                      │
└─────────────────────────────────┘
```

## Componentes

### 1. Interface CobrancaCriacaoStrategy

```java
package com.v.challenge.service.strategy;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaMetodoEnum;

public interface CobrancaCriacaoStrategy {
    Cobranca executar(Cobranca cobranca);
    CobrancaMetodoEnum getMetodo();
}
```

A interface define dois métodos:
- `executar`: recebe uma Cobranca e retorna a mesma entidade enriquecida com dados do gateway externo
- `getMetodo`: identifica qual método de pagamento a strategy atende (usado pelo Registry para construção do mapa)

### 2. PixCriacaoStrategy

```java
package com.v.challenge.service.strategy;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.integration.PagamentoGatewayClient;
import com.v.challenge.integration.PixCriacaoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PixCriacaoStrategy implements CobrancaCriacaoStrategy {

    private final PagamentoGatewayClient gatewayClient;

    @Override
    public Cobranca executar(Cobranca cobranca) {
        PixCriacaoResponse response = gatewayClient.criarPix(
            cobranca.getValorSolicitacao(),
            cobranca.getIdUsuario()
        );

        cobranca.setTxid(response.txid());
        cobranca.setCopiaECola(response.copiaECola());
        cobranca.setDataExpiracao(response.dataExpiracao());

        return cobranca;
    }

    @Override
    public CobrancaMetodoEnum getMetodo() {
        return CobrancaMetodoEnum.PIX;
    }
}
```

### 3. CartaoCriacaoStrategy

```java
package com.v.challenge.service.strategy;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.integration.CartaoTransacaoResponse;
import com.v.challenge.integration.PagamentoGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartaoCriacaoStrategy implements CobrancaCriacaoStrategy {

    private final PagamentoGatewayClient gatewayClient;

    @Override
    public Cobranca executar(Cobranca cobranca) {
        CartaoTransacaoResponse response = gatewayClient.iniciarTransacaoCartao(
            cobranca.getValorSolicitacao(),
            cobranca.getIdUsuario()
        );

        cobranca.setTransactionId(response.transactionId());

        if (response.requires3ds()) {
            cobranca.setAcsUrl(response.acsUrl());
            cobranca.setThreeDsPayload(response.threeDsPayload());
        }

        return cobranca;
    }

    @Override
    public CobrancaMetodoEnum getMetodo() {
        return CobrancaMetodoEnum.CARTAO_CREDITO;
    }
}
```

### 4. CobrancaCriacaoStrategyRegistry

```java
package com.v.challenge.service.strategy;

import com.v.challenge.domain.CobrancaMetodoEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CobrancaCriacaoStrategyRegistry {

    private final Map<CobrancaMetodoEnum, CobrancaCriacaoStrategy> strategies;

    public CobrancaCriacaoStrategyRegistry(List<CobrancaCriacaoStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(
                CobrancaCriacaoStrategy::getMetodo,
                Function.identity()
            ));
        log.info("Strategies registradas: {}", strategies.keySet());
    }

    public CobrancaCriacaoStrategy getStrategy(CobrancaMetodoEnum metodo) {
        CobrancaCriacaoStrategy strategy = strategies.get(metodo);
        if (strategy == null) {
            throw new IllegalArgumentException(
                "Strategy não encontrada para método: " + metodo
            );
        }
        return strategy;
    }
}
```

### 5. Clientes de Integração

#### PagamentoGatewayClient

```java
package com.v.challenge.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
public class PagamentoGatewayClient {

    public PixCriacaoResponse criarPix(BigDecimal valor, String idUsuario) {
        log.info("Criando PIX - valor: {}, usuario: {}", valor, idUsuario);
        return new PixCriacaoResponse(
            "PIX" + System.currentTimeMillis(),
            "00020126580014BR.GOV.BCB.PIX...",
            LocalDateTime.now().plusHours(2)
        );
    }

    public CartaoTransacaoResponse iniciarTransacaoCartao(BigDecimal valor, String idUsuario) {
        log.info("Iniciando transação cartão - valor: {}, usuario: {}", valor, idUsuario);
        return new CartaoTransacaoResponse(
            "TXN" + System.currentTimeMillis(),
            false,
            null,
            null
        );
    }
}
```

#### CheckoutValidationClient

```java
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
```

#### StatusConsultaExternaClient

```java
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
```

### 6. Records de Resposta

```java
package com.v.challenge.integration;

import java.time.LocalDateTime;

public record PixCriacaoResponse(
    String txid,
    String copiaECola,
    LocalDateTime dataExpiracao
) {}
```

```java
package com.v.challenge.integration;

public record CartaoTransacaoResponse(
    String transactionId,
    boolean requires3ds,
    String acsUrl,
    String threeDsPayload
) {}
```

```java
package com.v.challenge.integration;

public record CheckoutValidationResponse(
    boolean aprovado,
    String resultado,
    String threeDsResult
) {}
```

## Tratamento de Erros

| Cenário | Comportamento |
|---------|--------------|
| Método não registrado no Registry | Lança `IllegalArgumentException` com mensagem descritiva |
| Falha no gateway (futuro) | Propagar exceção para camada superior tratar |

## Decisões de Design

1. **getMetodo() na interface**: Permite auto-descoberta pelo Registry sem acoplar a configuração externa
2. **Records para responses**: Imutabilidade e clareza — perfeito para DTOs de integração
3. **Mocks com dados realistas**: Facilitam desenvolvimento e testes sem dependência de serviços externos
4. **Registry via construtor com List<>**: Spring injeta automaticamente todas as implementações de CobrancaCriacaoStrategy

## Correctness Properties

*Uma propriedade é uma característica ou comportamento que deve ser verdadeiro em todas as execuções válidas do sistema — essencialmente, uma declaração formal sobre o que o sistema deve fazer.*

### Property 1: PIX Strategy mapeia resposta completa do gateway

*Para qualquer* Cobranca com valorSolicitacao e idUsuario arbitrários, e *para qualquer* PixCriacaoResponse retornada pelo gateway, após executar a PixCriacaoStrategy, os campos `txid`, `copiaECola` e `dataExpiracao` na entidade Cobranca devem ser idênticos aos valores correspondentes da resposta do gateway.

**Validates: Requirements 2.1, 2.2**

### Property 2: Cartão Strategy mapeia transactionId

*Para qualquer* Cobranca com valorSolicitacao e idUsuario arbitrários, e *para qualquer* CartaoTransacaoResponse retornada pelo gateway, após executar a CartaoCriacaoStrategy, o campo `transactionId` na entidade Cobranca deve ser idêntico ao `transactionId` da resposta.

**Validates: Requirements 3.1, 3.2**

### Property 3: Cartão Strategy mapeia campos 3DS condicionalmente

*Para qualquer* CartaoTransacaoResponse onde `requires3ds == true`, após executar a CartaoCriacaoStrategy, os campos `acsUrl` e `threeDsPayload` na Cobranca devem ser idênticos aos da resposta. *Para qualquer* CartaoTransacaoResponse onde `requires3ds == false`, os campos `acsUrl` e `threeDsPayload` devem permanecer null.

**Validates: Requirements 3.3, 3.4**

### Property 4: Registry resolve strategy correta por método

*Para qualquer* conjunto de CobrancaCriacaoStrategy com métodos distintos registrados no Registry, invocar `getStrategy(metodo)` deve retornar a instância cuja `getMetodo()` retorna exatamente aquele `metodo`.

**Validates: Requirements 4.1, 4.2**
