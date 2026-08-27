# Documento de Requisitos

## Introdução

Este documento especifica os requisitos para a implementação do padrão Strategy aplicado à criação de cobranças por método de pagamento (PIX e Cartão de Crédito) no microserviço de bilhetagem. Inclui a interface de estratégia, implementações concretas, registry de auto-descoberta e clientes de integração mock.

## Glossário

- **Strategy**: Padrão de projeto que encapsula algoritmos intercambiáveis em classes separadas
- **CobrancaCriacaoStrategy**: Interface que define o contrato para criação de cobranças por método de pagamento
- **StrategyRegistry**: Componente que mapeia cada `CobrancaMetodoEnum` à sua respectiva implementação de Strategy
- **PagamentoGatewayClient**: Cliente mock que simula chamadas a gateways de pagamento externos
- **PIX**: Método de pagamento instantâneo via Banco Central do Brasil
- **Cartão de Crédito**: Método de pagamento via transação com cartão, opcionalmente com autenticação 3DS
- **Cobranca**: Entidade JPA que representa uma cobrança no sistema

## Requisitos

### Requisito 1: Interface de Estratégia de Criação

**User Story:** Como desenvolvedor, quero uma interface de estratégia para criação de cobranças, para que cada método de pagamento tenha sua lógica encapsulada e extensível.

#### Critérios de Aceitação

1. THE CobrancaCriacaoStrategy SHALL define o método `executar(Cobranca cobranca)` que recebe uma entidade Cobranca e retorna a mesma entidade enriquecida com dados do gateway
2. THE CobrancaCriacaoStrategy SHALL define o método `getMetodo()` que retorna o `CobrancaMetodoEnum` correspondente à implementação

### Requisito 2: Estratégia PIX

**User Story:** Como sistema, quero processar criações de cobrança PIX via uma estratégia dedicada, para que os dados específicos do PIX (txid, copia-e-cola, expiração) sejam preenchidos corretamente.

#### Critérios de Aceitação

1. WHEN a PixCriacaoStrategy executa uma cobrança, THE PixCriacaoStrategy SHALL invocar `PagamentoGatewayClient.criarPix` com o valor da solicitação e o ID do usuário da cobrança
2. WHEN o gateway retorna uma resposta PIX válida, THE PixCriacaoStrategy SHALL preencher os campos `txid`, `copiaECola` e `dataExpiracao` na entidade Cobranca
3. THE PixCriacaoStrategy SHALL retornar `CobrancaMetodoEnum.PIX` no método `getMetodo()`

### Requisito 3: Estratégia Cartão de Crédito

**User Story:** Como sistema, quero processar criações de cobrança por cartão de crédito via uma estratégia dedicada, para que os dados específicos do cartão (transactionId, dados 3DS) sejam preenchidos corretamente.

#### Critérios de Aceitação

1. WHEN a CartaoCriacaoStrategy executa uma cobrança, THE CartaoCriacaoStrategy SHALL invocar `PagamentoGatewayClient.iniciarTransacaoCartao` com o valor da solicitação e o ID do usuário da cobrança
2. WHEN o gateway retorna uma resposta de cartão válida, THE CartaoCriacaoStrategy SHALL preencher o campo `transactionId` na entidade Cobranca
3. WHEN a resposta do gateway indica que 3DS é obrigatório (`requires3ds == true`), THE CartaoCriacaoStrategy SHALL preencher os campos `acsUrl` e `threeDsPayload` na entidade Cobranca
4. WHEN a resposta do gateway indica que 3DS não é obrigatório (`requires3ds == false`), THE CartaoCriacaoStrategy SHALL manter os campos `acsUrl` e `threeDsPayload` como null na entidade Cobranca
5. THE CartaoCriacaoStrategy SHALL retornar `CobrancaMetodoEnum.CARTAO_CREDITO` no método `getMetodo()`

### Requisito 4: Registry de Estratégias

**User Story:** Como desenvolvedor, quero um registry que descobre automaticamente todas as estratégias registradas no container Spring, para que a seleção da estratégia correta seja automática baseada no método de pagamento.

#### Critérios de Aceitação

1. WHEN o StrategyRegistry é inicializado, THE StrategyRegistry SHALL construir um mapa interno associando cada `CobrancaMetodoEnum` à sua respectiva implementação de CobrancaCriacaoStrategy usando o resultado de `getMetodo()`
2. WHEN `getStrategy` é invocado com um método de pagamento existente no mapa, THE StrategyRegistry SHALL retornar a instância correspondente de CobrancaCriacaoStrategy
3. WHEN `getStrategy` é invocado com um método de pagamento inexistente no mapa, THE StrategyRegistry SHALL lançar uma `IllegalArgumentException` com mensagem descritiva incluindo o método solicitado

### Requisito 5: Clientes de Integração Mock

**User Story:** Como desenvolvedor, quero clientes de integração mock que simulam chamadas a gateways externos, para que o desenvolvimento e testes prossigam sem dependências externas.

#### Critérios de Aceitação

1. WHEN `PagamentoGatewayClient.criarPix` é invocado, THE PagamentoGatewayClient SHALL retornar um `PixCriacaoResponse` contendo txid não-nulo, copiaECola não-nula e dataExpiracao futura
2. WHEN `PagamentoGatewayClient.iniciarTransacaoCartao` é invocado, THE PagamentoGatewayClient SHALL retornar um `CartaoTransacaoResponse` contendo transactionId não-nulo
3. WHEN `CheckoutValidationClient.validarCheckout` é invocado, THE CheckoutValidationClient SHALL retornar um `CheckoutValidationResponse` com resultado da validação
4. WHEN `StatusConsultaExternaClient.consultarStatus` é invocado com um txid, THE StatusConsultaExternaClient SHALL retornar um `CobrancaStatusEnum` válido

### Requisito 6: DTOs de Resposta de Integração

**User Story:** Como desenvolvedor, quero records Java imutáveis para encapsular respostas dos gateways externos, para que os dados de integração tenham tipagem forte e sejam imutáveis.

#### Critérios de Aceitação

1. THE PixCriacaoResponse SHALL ser um record Java contendo os campos `txid` (String), `copiaECola` (String) e `dataExpiracao` (LocalDateTime)
2. THE CartaoTransacaoResponse SHALL ser um record Java contendo os campos `transactionId` (String), `requires3ds` (boolean), `acsUrl` (String) e `threeDsPayload` (String)
3. THE CheckoutValidationResponse SHALL ser um record Java contendo os campos `aprovado` (boolean), `resultado` (String) e `threeDsResult` (String)
