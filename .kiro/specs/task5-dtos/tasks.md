# Plano de Implementação: DTOs e Exception Handler

## Visão Geral

Implementação dos DTOs (request, response e evento Kafka) como Java records com Bean Validation, e do GlobalExceptionHandler com @RestControllerAdvice para tratamento centralizado de exceções no microserviço de bilhetagem.

## Tasks

- [ ] 1. Criar Request DTOs com Bean Validation
  - [x] 1.1 Criar CobrancaRequestDTO com validação e métodos de valor default
    - Criar `src/main/java/com/v/challenge/dto/CobrancaRequestDTO.java`
    - Java record com @NotNull @Positive BigDecimal valor, CobrancaTipoEnum tipo (nullable), CobrancaMetodoEnum metodo (nullable)
    - Implementar métodos `tipoEfetivo()` (default RECARGA) e `metodoEfetivo()` (default PIX)
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 1.2 Criar PixWebhookDTO e PixWebhookItemDTO
    - Criar `src/main/java/com/v/challenge/dto/PixWebhookDTO.java`
    - Criar `src/main/java/com/v/challenge/dto/PixWebhookItemDTO.java`
    - PixWebhookDTO: record com List<PixWebhookItemDTO> pix
    - PixWebhookItemDTO: record com String txid, @JsonFormat LocalDateTime horario, BigDecimal valor
    - _Requirements: 1.5_

  - [x] 1.3 Criar CheckoutValidateRequestDTO
    - Criar `src/main/java/com/v/challenge/dto/CheckoutValidateRequestDTO.java`
    - Java record com @NotBlank String cavv, @NotBlank String xid, @NotBlank String eci
    - _Requirements: 1.4_

  - [ ]* 1.4 Escrever property test para validação de CobrancaRequestDTO
    - **Property 1: Validação rejeita valores não-positivos**
    - Gerar BigDecimal nulos, zero e negativos; verificar que Validator produz ConstraintViolation
    - **Validates: Requirements 1.1**

  - [ ]* 1.5 Escrever property test para validação de CheckoutValidateRequestDTO
    - **Property 2: Validação rejeita strings em branco**
    - Gerar strings whitespace arbitrárias; verificar que Validator produz ConstraintViolation para cada campo
    - **Validates: Requirements 1.4**

  - [ ]* 1.6 Escrever property test para round-trip de PixWebhookDTO
    - **Property 3: Round-trip de serialização de PixWebhookDTO**
    - Gerar listas de tamanhos arbitrários; serializar e desserializar; verificar igualdade
    - **Validates: Requirements 1.5**

- [x] 2. Criar Response DTOs e ErrorResponse
  - [x] 2.1 Criar CobrancaBasicoResponseDTO
    - Criar `src/main/java/com/v/challenge/dto/CobrancaBasicoResponseDTO.java`
    - Java record com Long id, String txid, String copiaECola, LocalDateTime dataExpiracao, String transactionId
    - _Requirements: 2.1, 2.2_

  - [x] 2.2 Criar CobrancaCompletoResponseDTO
    - Criar `src/main/java/com/v/challenge/dto/CobrancaCompletoResponseDTO.java`
    - Java record com todos os campos de detalhe completo da cobrança
    - _Requirements: 2.3_

  - [x] 2.3 Criar ErrorResponse
    - Criar `src/main/java/com/v/challenge/dto/ErrorResponse.java`
    - Java record com String codigo, String mensagem
    - _Requirements: 3.5_

  - [ ]* 2.4 Escrever property test para serialização de CobrancaCompletoResponseDTO
    - **Property 4: Serialização de Response DTOs preserva todos os campos**
    - Gerar instâncias arbitrárias; serializar para JSON; verificar presença dos 11 campos
    - **Validates: Requirements 2.3, 2.4**

  - [ ]* 2.5 Escrever property test para ErrorResponse
    - **Property 7: ErrorResponse serializa exatamente dois campos**
    - Gerar pares de strings; serializar; verificar exatamente dois campos JSON
    - **Validates: Requirements 3.5**

- [x] 3. Criar Event DTO para Kafka
  - [x] 3.1 Criar CobrancaEventDTO
    - Criar `src/main/java/com/v/challenge/dto/CobrancaEventDTO.java`
    - Java record com Long cobrancaId, String idUsuario, CobrancaStatusEnum statusAtual, CobrancaStatusEnum statusAnterior, LocalDateTime timestamp, String eventoTipo
    - _Requirements: 4.1_

  - [ ]* 3.2 Escrever property test para round-trip de CobrancaEventDTO
    - **Property 8: Round-trip de serialização de CobrancaEventDTO**
    - Gerar instâncias arbitrárias; serializar e desserializar; verificar igualdade
    - **Validates: Requirements 4.1, 4.2**

- [x] 4. Checkpoint - Verificar compilação dos DTOs
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Implementar Exception e GlobalExceptionHandler
  - [x] 5.1 Criar CobrancaNaoEncontradaException
    - Criar `src/main/java/com/v/challenge/exception/CobrancaNaoEncontradaException.java`
    - Extends RuntimeException com construtor (String message)
    - _Requirements: 3.2_

  - [x] 5.2 Criar GlobalExceptionHandler
    - Criar `src/main/java/com/v/challenge/exception/GlobalExceptionHandler.java`
    - @RestControllerAdvice com handlers para LockIndisponivelException (422), CobrancaNaoEncontradaException (404), MethodArgumentNotValidException (400) e Exception genérica (500)
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [ ]* 5.3 Escrever property test para GlobalExceptionHandler com LockIndisponivelException
    - **Property 5: LockIndisponivelException preserva mensagem na resposta**
    - Gerar mensagens arbitrárias; invocar handler; verificar status 422 e mensagem preservada
    - **Validates: Requirements 3.1**

  - [ ]* 5.4 Escrever property test para GlobalExceptionHandler com exceção genérica
    - **Property 6: Exceções genéricas sempre retornam resposta fixa**
    - Gerar Exception com mensagens arbitrárias; invocar handler; verificar status 500 e mensagem fixa
    - **Validates: Requirements 3.3**

  - [ ]* 5.5 Escrever testes unitários para GlobalExceptionHandler
    - Testar CobrancaNaoEncontradaException retorna 404
    - Testar MethodArgumentNotValidException retorna 400 com detalhes
    - _Requirements: 3.2, 3.4_

- [x] 6. Checkpoint final - Verificar compilação e testes
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marcadas com `*` são opcionais e podem ser puladas para MVP mais rápido
- Cada task referencia requisitos específicos para rastreabilidade
- Checkpoints garantem validação incremental
- Property tests validam propriedades universais de corretude
- Testes unitários validam exemplos específicos e edge cases
- Linguagem de implementação: Java 17 com jqwik 1.8.2 para property-based testing
- Bean Validation já está incluído no spring-boot-starter-web (não adicionar dependência)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "2.1", "2.2", "2.3", "3.1"] },
    { "id": 1, "tasks": ["1.4", "1.5", "1.6", "2.4", "2.5", "3.2", "5.1"] },
    { "id": 2, "tasks": ["5.2"] },
    { "id": 3, "tasks": ["5.3", "5.4", "5.5"] }
  ]
}
```
