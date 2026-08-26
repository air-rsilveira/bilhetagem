# Plano de Implementação: Strategy - Criação de Cobrança por Método

## Visão Geral

Implementar o padrão Strategy para criação de cobranças, com estratégias concretas para PIX e Cartão de Crédito, registry de auto-descoberta via Spring, clientes de integração mock e records de resposta.

## Tasks

- [ ] 1. Criar records de resposta e clientes de integração
  - [x] 1.1 Criar records PixCriacaoResponse, CartaoTransacaoResponse e CheckoutValidationResponse
    - Criar `src/main/java/com/v/challenge/integration/PixCriacaoResponse.java` como record(String txid, String copiaECola, LocalDateTime dataExpiracao)
    - Criar `src/main/java/com/v/challenge/integration/CartaoTransacaoResponse.java` como record(String transactionId, boolean requires3ds, String acsUrl, String threeDsPayload)
    - Criar `src/main/java/com/v/challenge/integration/CheckoutValidationResponse.java` como record(boolean aprovado, String resultado, String threeDsResult)
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 1.2 Criar PagamentoGatewayClient
    - Criar `src/main/java/com/v/challenge/integration/PagamentoGatewayClient.java` como @Component
    - Implementar `criarPix(BigDecimal valor, String idUsuario)` retornando PixCriacaoResponse com dados mock realistas
    - Implementar `iniciarTransacaoCartao(BigDecimal valor, String idUsuario)` retornando CartaoTransacaoResponse com dados mock
    - _Requirements: 5.1, 5.2_

  - [x] 1.3 Criar CheckoutValidationClient e StatusConsultaExternaClient
    - Criar `src/main/java/com/v/challenge/integration/CheckoutValidationClient.java` como @Component com método `validarCheckout`
    - Criar `src/main/java/com/v/challenge/integration/StatusConsultaExternaClient.java` como @Component com método `consultarStatus`
    - _Requirements: 5.3, 5.4_

- [x] 2. Implementar interface e strategies
  - [x] 2.1 Criar interface CobrancaCriacaoStrategy
    - Criar `src/main/java/com/v/challenge/service/strategy/CobrancaCriacaoStrategy.java`
    - Definir `Cobranca executar(Cobranca cobranca)` e `CobrancaMetodoEnum getMetodo()`
    - _Requirements: 1.1, 1.2_

  - [x] 2.2 Implementar PixCriacaoStrategy
    - Criar `src/main/java/com/v/challenge/service/strategy/PixCriacaoStrategy.java` como @Component
    - Injetar PagamentoGatewayClient via construtor (@RequiredArgsConstructor)
    - Implementar `executar`: chamar `gatewayClient.criarPix`, mapear resposta para campos da Cobranca (txid, copiaECola, dataExpiracao)
    - Implementar `getMetodo` retornando `CobrancaMetodoEnum.PIX`
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 2.3 Implementar CartaoCriacaoStrategy
    - Criar `src/main/java/com/v/challenge/service/strategy/CartaoCriacaoStrategy.java` como @Component
    - Injetar PagamentoGatewayClient via construtor (@RequiredArgsConstructor)
    - Implementar `executar`: chamar `gatewayClient.iniciarTransacaoCartao`, mapear transactionId, condicionalmente mapear acsUrl/threeDsPayload se requires3ds==true
    - Implementar `getMetodo` retornando `CobrancaMetodoEnum.CARTAO_CREDITO`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 2.4 Escrever property test para PixCriacaoStrategy
    - **Property 1: PIX Strategy mapeia resposta completa do gateway**
    - Gerar Cobranca com valorSolicitacao e idUsuario aleatórios, mockar PagamentoGatewayClient com PixCriacaoResponse aleatório, verificar mapeamento completo
    - **Validates: Requirements 2.1, 2.2**

  - [ ]* 2.5 Escrever property test para CartaoCriacaoStrategy
    - **Property 2: Cartão Strategy mapeia transactionId**
    - **Property 3: Cartão Strategy mapeia campos 3DS condicionalmente**
    - Gerar CartaoTransacaoResponse com requires3ds true/false, verificar mapeamento condicional
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4**

- [x] 3. Implementar Strategy Registry
  - [x] 3.1 Criar CobrancaCriacaoStrategyRegistry
    - Criar `src/main/java/com/v/challenge/service/strategy/CobrancaCriacaoStrategyRegistry.java` como @Component
    - Receber `List<CobrancaCriacaoStrategy>` no construtor, construir `Map<CobrancaMetodoEnum, CobrancaCriacaoStrategy>` usando `getMetodo()`
    - Implementar `getStrategy(CobrancaMetodoEnum metodo)` com `IllegalArgumentException` se não encontrado
    - _Requirements: 4.1, 4.2, 4.3_

  - [ ]* 3.2 Escrever property test para StrategyRegistry
    - **Property 4: Registry resolve strategy correta por método**
    - Gerar lista de strategies mock com métodos distintos, verificar resolução correta
    - **Validates: Requirements 4.1, 4.2**

- [ ] 4. Testes unitários
  - [ ]* 4.1 Escrever testes unitários para PixCriacaoStrategy e CartaoCriacaoStrategy
    - Testar PixCriacaoStrategy preenche txid, copiaECola, dataExpiracao com mock
    - Testar CartaoCriacaoStrategy preenche transactionId
    - Testar CartaoCriacaoStrategy com requires3ds=true preenche acsUrl e threeDsPayload
    - Testar CartaoCriacaoStrategy com requires3ds=false mantém campos 3DS null
    - Testar getMetodo() retorna valor correto em cada strategy
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 4.2 Escrever testes unitários para CobrancaCriacaoStrategyRegistry
    - Testar que registry resolve strategy correta para PIX
    - Testar que registry resolve strategy correta para CARTAO_CREDITO
    - Testar que registry lança IllegalArgumentException para método não registrado
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 5. Checkpoint final
  - Garantir que todos os testes passam, perguntar ao usuário se há dúvidas.

## Notes

- Tasks marcadas com `*` são opcionais e podem ser puladas para MVP mais rápido
- Linguagem de implementação: Java 17 com Spring Boot 3.2.0
- Framework de property testing: jqwik 1.8.2 (já configurado no pom.xml)
- Cada task referencia requisitos específicos para rastreabilidade
- Os clientes de integração são mocks — retornam dados hardcoded simulando gateways reais
- O Registry usa injeção de List<> do Spring para auto-descoberta de strategies

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3"] },
    { "id": 3, "tasks": ["2.4", "2.5", "3.1"] },
    { "id": 4, "tasks": ["3.2", "4.1", "4.2"] }
  ]
}
```
