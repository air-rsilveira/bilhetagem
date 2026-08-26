# Plano de Implementação: Task 2 - Domínio (Entidade e Enums)

## Visão Geral

Implementar a camada de domínio do microserviço de bilhetagem: enums com códigos numéricos, entidade JPA `Cobranca` com Lombok, e repositório com queries customizadas para busca por txid, transactionId e versionamento.

## Tasks

- [x] 1. Criar enums de domínio
  - [x] 1.1 Criar CobrancaTipoEnum e CobrancaMetodoEnum
    - Criar `src/main/java/com/v/challenge/domain/CobrancaTipoEnum.java` com valores RECARGA, RECARGA_TERCEIROS, ENVIO_CARTAO
    - Criar `src/main/java/com/v/challenge/domain/CobrancaMetodoEnum.java` com valores PIX, CARTAO_CREDITO
    - _Requirements: 1.1, 1.2_

  - [x] 1.2 Criar CobrancaStatusEnum com códigos numéricos
    - Criar `src/main/java/com/v/challenge/domain/CobrancaStatusEnum.java`
    - Implementar campo `code`, construtor com código, `getCode()` via Lombok @Getter
    - Implementar método estático `fromCode(int code)` que retorna o enum ou lança IllegalArgumentException
    - Códigos: SOLICITADA=2, AGUARDANDO_PAGAMENTO=3, EM_PROCESSAMENTO=4, FINALIZADA=5, EXPIRADA=6, CANCELADA=7, ERRO_APROVACAO_PEDIDO=8, EM_REPROCESSAMENTO=9, ERRO_ANALISE_PENDENTE=10
    - _Requirements: 1.3, 1.4, 1.5, 1.6_

  - [x] 1.3 Escrever testes unitários para CobrancaStatusEnum
    - Criar `src/test/java/com/v/challenge/domain/CobrancaStatusEnumTest.java`
    - Testar que cada enum retorna o código correto via getCode()
    - Testar round-trip: fromCode(valor.getCode()) == valor para todos os valores
    - Testar que fromCode com código inválido lança IllegalArgumentException
    - **Property 1: Round-trip getCode/fromCode**
    - **Property 2: Códigos inválidos rejeitados**
    - **Validates: Requirements 1.3, 1.4, 1.5, 1.6**

- [x] 2. Criar entidade JPA Cobranca
  - [x] 2.1 Implementar entidade Cobranca com todos os campos
    - Criar `src/main/java/com/v/challenge/domain/Cobranca.java`
    - Usar anotações Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor, @Builder
    - Mapear @Entity, @Table(name = "cobranca")
    - @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id
    - @Column(nullable = false) para idUsuario e nomeSolicitante
    - @Enumerated(EnumType.STRING) para tipo, metodo, status
    - BigDecimal para valorSolicitacao, valorPago
    - String para txid, transactionId, acsUrl
    - @Column(columnDefinition = "TEXT") para copiaECola e threeDsPayload
    - LocalDateTime para dataCriacao, dataExpiracao, dataFinalizada
    - Long idCobrancaOrigem (nullable, sem anotação NOT NULL)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

- [x] 3. Criar CobrancaRepository com queries customizadas
  - [x] 3.1 Implementar CobrancaRepository
    - Criar `src/main/java/com/v/challenge/repository/CobrancaRepository.java`
    - Estender JpaRepository<Cobranca, Long>
    - Criar @Query JPQL `findAllVersoes` que busca cobranças onde idCobrancaOrigem = :id OR id = :id, ordenadas por dataCriacao DESC
    - Criar método default `findVersaoMaisRecente(Long id)` que retorna o primeiro elemento de findAllVersoes
    - Criar `findTopByTxidOrderByDataCriacaoDesc(String txid)` retornando Optional<Cobranca>
    - Criar `findByTransactionId(String transactionId)` retornando Optional<Cobranca>
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 4. Checkpoint - Verificar compilação
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Testes de repositório
  - [x] 5.1 Escrever testes de integração do CobrancaRepository
    - Criar `src/test/java/com/v/challenge/repository/CobrancaRepositoryTest.java`
    - Usar @DataJpaTest + @ActiveProfiles("test") para H2 in-memory
    - Testar persistência da entidade com id gerado automaticamente
    - Testar findTopByTxidOrderByDataCriacaoDesc retorna a mais recente entre múltiplas com mesmo txid
    - Testar findByTransactionId retorna cobrança correspondente
    - Testar findVersaoMaisRecente retorna cobrança filha mais recente quando existem filhas
    - Testar findVersaoMaisRecente retorna a original quando não existem filhas
    - Testar queries retornam Optional.empty() quando não encontram resultados
    - **Property 4: Versionamento retorna mais recente**
    - **Property 5: Busca por txid retorna mais recente**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

- [x] 6. Checkpoint final - Todos os testes passam
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marcadas com `*` são opcionais e podem ser puladas para MVP mais rápido
- O `schema.sql` já existe e não precisa ser modificado
- Para testes, usar `@ActiveProfiles("test")` que ativa H2 in-memory e desabilita Redis/Kafka
- A NamingStrategy padrão do Hibernate converte camelCase para snake_case automaticamente
- O método `findVersaoMaisRecente` usa um default method no repository para contornar a limitação de LIMIT no JPQL
- Property tests para este domínio têm set finito (enums) — cobrir todos os valores é equivalente a property test

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "2.1"] },
    { "id": 2, "tasks": ["3.1"] },
    { "id": 3, "tasks": ["5.1"] }
  ]
}
```
