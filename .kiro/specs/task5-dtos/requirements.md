# Documento de Requisitos

## Introdução

Este documento especifica os requisitos para criação dos DTOs (Data Transfer Objects) de request/response e do tratamento global de exceções para o microserviço de bilhetagem. Os DTOs utilizam Java records com anotações de Bean Validation, e o exception handler centraliza o mapeamento de exceções para respostas HTTP padronizadas.

## Glossário

- **Sistema**: O microserviço de bilhetagem (bilhetagem)
- **DTO**: Data Transfer Object — objeto imutável (Java record) utilizado para transferência de dados entre camadas
- **GlobalExceptionHandler**: Componente @RestControllerAdvice que intercepta exceções e retorna respostas HTTP padronizadas
- **Bean Validation**: Mecanismo de validação declarativa via anotações jakarta.validation
- **ErrorResponse**: DTO padronizado para respostas de erro contendo código e mensagem
- **Cobrança**: Entidade de domínio representando uma solicitação de cobrança/pagamento

## Requisitos

### Requisito 1

**User Story:** Como desenvolvedor da API, eu quero DTOs de request com Bean Validation, para que dados inválidos sejam rejeitados antes de chegar à camada de serviço.

#### Critérios de Aceitação

1. WHEN o Sistema recebe um CobrancaRequestDTO com campo valor nulo ou não-positivo, THEN o Sistema SHALL rejeitar a requisição com status HTTP 400 e mensagem descritiva do campo inválido
2. WHEN o Sistema recebe um CobrancaRequestDTO com campo tipo nulo, THEN o Sistema SHALL aceitar a requisição e utilizar o valor padrão RECARGA
3. WHEN o Sistema recebe um CobrancaRequestDTO com campo metodo nulo, THEN o Sistema SHALL aceitar a requisição e utilizar o valor padrão PIX
4. WHEN o Sistema recebe um CheckoutValidateRequestDTO com qualquer campo obrigatório em branco, THEN o Sistema SHALL rejeitar a requisição com status HTTP 400 e mensagem descritiva do campo inválido
5. WHEN o Sistema recebe um PixWebhookDTO, THEN o Sistema SHALL desserializar corretamente a lista de itens PIX com txid, horario e valor

### Requisito 2

**User Story:** Como consumidor da API, eu quero respostas padronizadas nos endpoints de cobrança, para que eu possa interpretar os dados retornados de forma consistente.

#### Critérios de Aceitação

1. WHEN o Sistema retorna uma cobrança recém-criada (PIX), THEN o Sistema SHALL serializar um CobrancaBasicoResponseDTO contendo id, txid, copiaECola, dataExpiracao e transactionId
2. WHEN o Sistema retorna uma cobrança recém-criada (Cartão), THEN o Sistema SHALL serializar um CobrancaBasicoResponseDTO contendo id, txid, copiaECola (nulo para cartão), dataExpiracao e transactionId
3. WHEN o Sistema retorna detalhes completos de uma cobrança, THEN o Sistema SHALL serializar um CobrancaCompletoResponseDTO contendo id, txid, idUsuario, tipo, metodo, status, valorSolicitado, valorPago, dataCriacao, dataExpiracao e dataFinalizada
4. WHEN o Sistema serializa qualquer DTO de resposta para JSON, THEN o Sistema SHALL produzir campos com nomes em camelCase e valores LocalDateTime no formato ISO-8601

### Requisito 3

**User Story:** Como desenvolvedor da API, eu quero um tratamento centralizado de exceções, para que todas as respostas de erro sigam um formato padronizado com códigos HTTP corretos.

#### Critérios de Aceitação

1. WHEN uma LockIndisponivelException é lançada durante o processamento de uma requisição, THEN o Sistema SHALL retornar status HTTP 422 com ErrorResponse contendo codigo "LOCK_INDISPONIVEL" e a mensagem da exceção
2. WHEN uma CobrancaNaoEncontradaException é lançada durante o processamento de uma requisição, THEN o Sistema SHALL retornar status HTTP 404 com ErrorResponse contendo codigo "COBRANCA_NAO_ENCONTRADA" e mensagem "Cobrança não encontrada"
3. WHEN uma exceção genérica não-mapeada é lançada durante o processamento de uma requisição, THEN o Sistema SHALL retornar status HTTP 500 com ErrorResponse contendo codigo "ERRO_INTERNO" e mensagem "Erro ao criar cobrança."
4. WHEN uma MethodArgumentNotValidException é lançada (falha de Bean Validation), THEN o Sistema SHALL retornar status HTTP 400 com ErrorResponse contendo codigo "VALIDACAO_FALHOU" e detalhes dos campos inválidos
5. THE ErrorResponse SHALL conter exatamente dois campos: codigo (String) e mensagem (String)

### Requisito 4

**User Story:** Como sistema de mensageria, eu quero um DTO de evento Kafka padronizado, para que consumidores downstream possam processar transições de status de forma consistente.

#### Critérios de Aceitação

1. THE CobrancaEventDTO SHALL conter os campos cobrancaId (Long), idUsuario (String), statusAtual (CobrancaStatusEnum), statusAnterior (CobrancaStatusEnum), timestamp (LocalDateTime) e eventoTipo (String)
2. WHEN o Sistema serializa um CobrancaEventDTO para JSON, THEN o Sistema SHALL produzir um payload válido com todos os campos preenchidos e serializáveis pelo JsonSerializer do Kafka
