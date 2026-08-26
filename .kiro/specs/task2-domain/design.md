# Documento de Design: Task 2 - Domínio (Entidade e Enums)

## Visão Geral

Este documento descreve a arquitetura e implementação da camada de domínio do microserviço de bilhetagem, incluindo os enums de negócio, a entidade JPA `Cobranca` e o repositório com queries customizadas para versionamento e busca.

## Arquitetura

### Estrutura de Pacotes

```
src/main/java/com/v/challenge/
├── domain/
│   ├── Cobranca.java              # Entidade JPA
│   ├── CobrancaTipoEnum.java      # Enum de tipo
│   ├── CobrancaMetodoEnum.java    # Enum de método de pagamento
│   └── CobrancaStatusEnum.java    # Enum de status com códigos
└── repository/
    └── CobrancaRepository.java    # Interface JPA Repository
```

## Componentes

### 1. CobrancaTipoEnum

Enum simples sem código numérico, representando os tipos de cobrança disponíveis.

```java
package com.v.challenge.domain;

public enum CobrancaTipoEnum {
    RECARGA,
    RECARGA_TERCEIROS,
    ENVIO_CARTAO
}
```

### 2. CobrancaMetodoEnum

Enum simples representando os métodos de pagamento.

```java
package com.v.challenge.domain;

public enum CobrancaMetodoEnum {
    PIX,
    CARTAO_CREDITO
}
```

### 3. CobrancaStatusEnum

Enum com código numérico associado. Fornece métodos `getCode()` para obter o código e `fromCode(int)` para converter código em enum.

```java
package com.v.challenge.domain;

import lombok.Getter;

@Getter
public enum CobrancaStatusEnum {
    SOLICITADA(2),
    AGUARDANDO_PAGAMENTO(3),
    EM_PROCESSAMENTO(4),
    FINALIZADA(5),
    EXPIRADA(6),
    CANCELADA(7),
    ERRO_APROVACAO_PEDIDO(8),
    EM_REPROCESSAMENTO(9),
    ERRO_ANALISE_PENDENTE(10);

    private final int code;

    CobrancaStatusEnum(int code) {
        this.code = code;
    }

    public static CobrancaStatusEnum fromCode(int code) {
        for (CobrancaStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Código de status inválido: " + code);
    }
}
```

### 4. Entidade Cobranca

Entidade JPA mapeada para a tabela `cobranca`. Usa Lombok para boilerplate.

```java
package com.v.challenge.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cobranca")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String idUsuario;

    @Column(nullable = false)
    private String nomeSolicitante;

    @Enumerated(EnumType.STRING)
    private CobrancaTipoEnum tipo;

    @Enumerated(EnumType.STRING)
    private CobrancaMetodoEnum metodo;

    @Enumerated(EnumType.STRING)
    private CobrancaStatusEnum status;

    private BigDecimal valorSolicitacao;
    private BigDecimal valorPago;

    private String txid;

    @Column(columnDefinition = "TEXT")
    private String copiaECola;

    private String transactionId;
    private String acsUrl;

    @Column(columnDefinition = "TEXT")
    private String threeDsPayload;

    private LocalDateTime dataCriacao;
    private LocalDateTime dataExpiracao;
    private LocalDateTime dataFinalizada;

    private Long idCobrancaOrigem;
}
```

### 5. CobrancaRepository

Interface JPA com queries customizadas. A query `findVersaoMaisRecente` usa `List` como retorno e o caller pega o primeiro elemento, pois JPQL não suporta LIMIT diretamente.

```java
package com.v.challenge.repository;

import com.v.challenge.domain.Cobranca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CobrancaRepository extends JpaRepository<Cobranca, Long> {

    @Query("SELECT c FROM Cobranca c WHERE (c.idCobrancaOrigem = :id OR c.id = :id) ORDER BY c.dataCriacao DESC")
    List<Cobranca> findAllVersoes(@Param("id") Long id);

    default Optional<Cobranca> findVersaoMaisRecente(Long id) {
        List<Cobranca> versoes = findAllVersoes(id);
        return versoes.isEmpty() ? Optional.empty() : Optional.of(versoes.get(0));
    }

    Optional<Cobranca> findTopByTxidOrderByDataCriacaoDesc(String txid);

    Optional<Cobranca> findByTransactionId(String transactionId);
}
```

## Modelo de Dados

A tabela `cobranca` já existe conforme `schema.sql`:

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGSERIAL | PRIMARY KEY |
| id_usuario | VARCHAR(255) | NOT NULL |
| nome_solicitante | VARCHAR(255) | NOT NULL |
| tipo | VARCHAR(50) | NOT NULL |
| metodo | VARCHAR(50) | NOT NULL |
| status | VARCHAR(50) | NOT NULL |
| valor_solicitacao | NUMERIC(19,2) | |
| valor_pago | NUMERIC(19,2) | |
| txid | VARCHAR(255) | |
| copia_e_cola | TEXT | |
| transaction_id | VARCHAR(255) | |
| acs_url | VARCHAR(500) | |
| three_ds_payload | TEXT | |
| data_criacao | TIMESTAMP | NOT NULL |
| data_expiracao | TIMESTAMP | |
| data_finalizada | TIMESTAMP | |
| id_cobranca_origem | BIGINT | FK → cobranca(id) |

### Índices

- `idx_cobranca_txid` em `txid`
- `idx_cobranca_transaction_id` em `transaction_id`
- `idx_cobranca_id_usuario` em `id_usuario`
- `idx_cobranca_id_origem` em `id_cobranca_origem`

## Mapeamento JPA ↔ Banco

O Hibernate faz o mapeamento de camelCase para snake_case por padrão (NamingStrategy):
- `idUsuario` → `id_usuario`
- `nomeSolicitante` → `nome_solicitante`
- `valorSolicitacao` → `valor_solicitacao`
- `copiaECola` → `copia_e_cola`
- `transactionId` → `transaction_id`
- `threeDsPayload` → `three_ds_payload`
- `dataCriacao` → `data_criacao`
- `idCobrancaOrigem` → `id_cobranca_origem`

## Tratamento de Erros

- `fromCode()` com código inválido: lança `IllegalArgumentException` com mensagem descritiva
- Persistência sem campos obrigatórios: JPA lança `ConstraintViolationException`
- Query sem resultados: retorna `Optional.empty()`

## Estratégia de Testes

### Testes Unitários (sem contexto Spring)
- Verificação de valores e códigos dos enums
- Round-trip de `getCode()` / `fromCode()`
- Validação de exceção para códigos inválidos

### Testes de Repositório (com H2 in-memory)
- Usar `@DataJpaTest` + `@ActiveProfiles("test")` para H2
- Testar queries customizadas com dados de cenário
- Verificar versionamento com cobranças filhas

## Correctness Properties

*Uma propriedade é uma característica ou comportamento que deve ser verdadeiro em todas as execuções válidas do sistema — essencialmente, uma declaração formal sobre o que o sistema deve fazer.*

### Property 1: Round-trip getCode/fromCode

*Para qualquer* valor do CobrancaStatusEnum, chamar `fromCode(valor.getCode())` deve retornar o próprio valor original.

**Validates: Requirements 1.4, 1.5**

### Property 2: Códigos inválidos rejeitados

*Para qualquer* inteiro que não pertença ao conjunto {2, 3, 4, 5, 6, 7, 8, 9, 10}, chamar `fromCode()` deve lançar IllegalArgumentException.

**Validates: Requirements 1.6**

### Property 3: Persistência BigDecimal preserva precisão

*Para qualquer* valor BigDecimal com até 2 casas decimais e até 17 dígitos inteiros, persistir uma Cobranca e recuperá-la deve preservar o valor exato de valorSolicitacao e valorPago.

**Validates: Requirements 2.4**

### Property 4: Versionamento retorna mais recente

*Para qualquer* cobrança original com N cobranças filhas (N ≥ 1) que referenciam seu id via idCobrancaOrigem, a query findVersaoMaisRecente deve retornar a cobrança com a dataCriacao mais recente entre todas (original + filhas).

**Validates: Requirements 3.3**

### Property 5: Busca por txid retorna mais recente

*Para qualquer* conjunto de cobranças com o mesmo txid e datas de criação distintas, findTopByTxidOrderByDataCriacaoDesc deve retornar a cobrança com a dataCriacao mais recente.

**Validates: Requirements 3.1**
