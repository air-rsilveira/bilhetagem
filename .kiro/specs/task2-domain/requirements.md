# Documento de Requisitos

## Introdução

Este documento especifica os requisitos para a camada de domínio do microserviço de bilhetagem, incluindo a entidade JPA `Cobranca`, os enums de tipo/método/status com códigos numéricos, e o repositório JPA com queries customizadas para busca por txid, transactionId e versionamento.

## Glossário

- **Cobranca**: Entidade JPA principal que representa uma cobrança de bilhetagem no sistema.
- **CobrancaTipoEnum**: Enum que define os tipos de cobrança (RECARGA, RECARGA_TERCEIROS, ENVIO_CARTAO).
- **CobrancaMetodoEnum**: Enum que define os métodos de pagamento (PIX, CARTAO_CREDITO).
- **CobrancaStatusEnum**: Enum que define os status possíveis de uma cobrança, cada um com um código numérico associado.
- **idCobrancaOrigem**: Campo nullable na entidade Cobranca que referencia a cobrança original, usado para versionamento.
- **txid**: Identificador de transação PIX gerado pelo PSP.
- **transactionId**: Identificador de transação de cartão de crédito.
- **Versionamento**: Mecanismo onde uma nova cobrança referencia a original via `idCobrancaOrigem`, permitindo recuperar a versão mais recente.
- **CobrancaRepository**: Interface JPA que fornece operações de persistência e queries customizadas para a entidade Cobranca.

## Requisitos

### Requisito 1

**User Story:** Como desenvolvedor, eu quero ter enums de domínio com códigos numéricos definidos, para que os valores de tipo, método e status da cobrança sejam padronizados e mapeáveis a códigos de sistema.

#### Critérios de Aceite

1. THE CobrancaTipoEnum SHALL definir exatamente os valores RECARGA, RECARGA_TERCEIROS e ENVIO_CARTAO
2. THE CobrancaMetodoEnum SHALL definir exatamente os valores PIX e CARTAO_CREDITO
3. THE CobrancaStatusEnum SHALL definir os valores SOLICITADA com código 2, AGUARDANDO_PAGAMENTO com código 3, EM_PROCESSAMENTO com código 4, FINALIZADA com código 5, EXPIRADA com código 6, CANCELADA com código 7, ERRO_APROVACAO_PEDIDO com código 8, EM_REPROCESSAMENTO com código 9 e ERRO_ANALISE_PENDENTE com código 10
4. WHEN o método getCode é invocado em qualquer valor do CobrancaStatusEnum, THEN o CobrancaStatusEnum SHALL retornar o código numérico correspondente ao valor
5. WHEN um código numérico válido é passado para o método fromCode do CobrancaStatusEnum, THEN o CobrancaStatusEnum SHALL retornar o valor enum correspondente
6. WHEN um código numérico inválido é passado para o método fromCode do CobrancaStatusEnum, THEN o CobrancaStatusEnum SHALL lançar uma IllegalArgumentException

### Requisito 2

**User Story:** Como desenvolvedor, eu quero uma entidade JPA `Cobranca` mapeada para a tabela `cobranca` do PostgreSQL, para que os dados de cobrança sejam persistidos e recuperados corretamente.

#### Critérios de Aceite

1. THE Cobranca SHALL ser mapeada para a tabela "cobranca" com id gerado via IDENTITY strategy
2. THE Cobranca SHALL conter os campos obrigatórios idUsuario e nomeSolicitante com constraint NOT NULL
3. THE Cobranca SHALL conter campos enum tipo, metodo e status persistidos como STRING
4. THE Cobranca SHALL conter campos monetários valorSolicitacao e valorPago do tipo BigDecimal
5. THE Cobranca SHALL conter campos de texto txid, copiaECola (TEXT), transactionId, acsUrl e threeDsPayload (TEXT)
6. THE Cobranca SHALL conter campos temporais dataCriacao, dataExpiracao e dataFinalizada do tipo LocalDateTime
7. THE Cobranca SHALL conter o campo nullable idCobrancaOrigem do tipo Long para suportar versionamento

### Requisito 3

**User Story:** Como desenvolvedor, eu quero um repositório JPA com queries customizadas, para que eu possa buscar cobranças por txid, transactionId e recuperar a versão mais recente de uma cobrança.

#### Critérios de Aceite

1. WHEN uma busca por txid é executada, THEN o CobrancaRepository SHALL retornar a cobrança mais recente que possua o txid informado
2. WHEN uma busca por transactionId é executada, THEN o CobrancaRepository SHALL retornar a cobrança que possua o transactionId informado
3. WHEN uma busca pela versão mais recente é executada com um id, THEN o CobrancaRepository SHALL retornar a cobrança com dataCriacao mais recente entre a cobrança original (com o id informado) e todas as cobranças que referenciam esse id via idCobrancaOrigem
4. WHEN o id informado na busca por versão mais recente não possui cobranças filhas, THEN o CobrancaRepository SHALL retornar a própria cobrança original
5. WHEN nenhuma cobrança é encontrada para o txid informado, THEN o CobrancaRepository SHALL retornar Optional vazio
6. WHEN nenhuma cobrança é encontrada para o transactionId informado, THEN o CobrancaRepository SHALL retornar Optional vazio
