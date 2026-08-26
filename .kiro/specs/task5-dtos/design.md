# Documento de Design: DTOs e Exception Handler

## Visão Geral da Arquitetura

Este módulo implementa a camada de transferência de dados (DTOs) e o tratamento centralizado de exceções para o microserviço de bilhetagem. Utiliza Java records para imutabilidade e Bean Validation (jakarta.validation) para validação declarativa. O GlobalExceptionHandler (@RestControllerAdvice) intercepta exceções e retorna respostas padronizadas.

## Componentes

### 1. Request DTOs

Pacote: `com.v.challenge.dto`

```java
// CobrancaRequestDTO.java
package com.v.challenge.dto;

import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.domain.CobrancaTipoEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CobrancaRequestDTO(
    @NotNull @Positive BigDecimal valor,
    CobrancaTipoEnum tipo,
    CobrancaMetodoEnum metodo
) {
    public CobrancaTipoEnum tipoEfetivo() {
        return tipo != null ? tipo : CobrancaTipoEnum.RECARGA;
    }

    public CobrancaMetodoEnum metodoEfetivo() {
        return metodo != null ? metodo : CobrancaMetodoEnum.PIX;
    }
}
```

```java
// PixWebhookDTO.java
package com.v.challenge.dto;

import java.util.List;

public record PixWebhookDTO(
    List<PixWebhookItemDTO> pix
) {}
```

```java
// PixWebhookItemDTO.java
package com.v.challenge.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PixWebhookItemDTO(
    String txid,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    LocalDateTime horario,
    BigDecimal valor
) {}
```

```java
// CheckoutValidateRequestDTO.java
package com.v.challenge.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutValidateRequestDTO(
    @NotBlank String cavv,
    @NotBlank String xid,
    @NotBlank String eci
) {}
```

### 2. Response DTOs

```java
// CobrancaBasicoResponseDTO.java
package com.v.challenge.dto;

import java.time.LocalDateTime;

public record CobrancaBasicoResponseDTO(
    Long id,
    String txid,
    String copiaECola,
    LocalDateTime dataExpiracao,
    String transactionId
) {}
```

```java
// CobrancaCompletoResponseDTO.java
package com.v.challenge.dto;

import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.domain.CobrancaStatusEnum;
import com.v.challenge.domain.CobrancaTipoEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CobrancaCompletoResponseDTO(
    Long id,
    String txid,
    String idUsuario,
    CobrancaTipoEnum tipo,
    CobrancaMetodoEnum metodo,
    CobrancaStatusEnum status,
    BigDecimal valorSolicitado,
    BigDecimal valorPago,
    LocalDateTime dataCriacao,
    LocalDateTime dataExpiracao,
    LocalDateTime dataFinalizada
) {}
```

```java
// ErrorResponse.java
package com.v.challenge.dto;

public record ErrorResponse(
    String codigo,
    String mensagem
) {}
```

### 3. Event DTO

```java
// CobrancaEventDTO.java
package com.v.challenge.dto;

import com.v.challenge.domain.CobrancaStatusEnum;
import java.time.LocalDateTime;

public record CobrancaEventDTO(
    Long cobrancaId,
    String idUsuario,
    CobrancaStatusEnum statusAtual,
    CobrancaStatusEnum statusAnterior,
    LocalDateTime timestamp,
    String eventoTipo
) {}
```

### 4. Exception

```java
// CobrancaNaoEncontradaException.java
package com.v.challenge.exception;

public class CobrancaNaoEncontradaException extends RuntimeException {
    public CobrancaNaoEncontradaException(String message) {
        super(message);
    }
}
```

### 5. GlobalExceptionHandler

```java
// GlobalExceptionHandler.java
package com.v.challenge.exception;

import com.v.challenge.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LockIndisponivelException.class)
    public ResponseEntity<ErrorResponse> handleLockIndisponivel(LockIndisponivelException ex) {
        return ResponseEntity.status(422)
            .body(new ErrorResponse("LOCK_INDISPONIVEL", ex.getMessage()));
    }

    @ExceptionHandler(CobrancaNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleCobrancaNaoEncontrada(CobrancaNaoEncontradaException ex) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("COBRANCA_NAO_ENCONTRADA", "Cobrança não encontrada"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String detalhes = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.status(400)
            .body(new ErrorResponse("VALIDACAO_FALHOU", detalhes));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return ResponseEntity.status(500)
            .body(new ErrorResponse("ERRO_INTERNO", "Erro ao criar cobrança."));
    }
}
```

## Interfaces e Contratos

### Mapeamento de Exceções

| Exceção | HTTP Status | Código | Mensagem |
|---------|-------------|--------|----------|
| LockIndisponivelException | 422 | LOCK_INDISPONIVEL | Mensagem dinâmica da exceção |
| CobrancaNaoEncontradaException | 404 | COBRANCA_NAO_ENCONTRADA | "Cobrança não encontrada" |
| MethodArgumentNotValidException | 400 | VALIDACAO_FALHOU | Detalhes dos campos inválidos |
| Exception (genérica) | 500 | ERRO_INTERNO | "Erro ao criar cobrança." |

### Serialização JSON

- Spring Boot usa Jackson por padrão (incluído no spring-boot-starter-web)
- LocalDateTime serializado como ISO-8601 (configuração padrão do Jackson com JavaTimeModule)
- Enums serializados como String (padrão Jackson para enums)
- Campos nulos são incluídos na serialização (padrão Jackson)

### Valores Default em CobrancaRequestDTO

Os métodos `tipoEfetivo()` e `metodoEfetivo()` fornecem valores default sem alterar o record original. A camada de serviço deve chamar esses métodos ao invés de acessar diretamente `tipo()` e `metodo()`.

## Modelo de Dados

Os DTOs mapeiam diretamente para/de a entidade `Cobranca`:

| Campo Entidade | DTO Request | DTO Response Básico | DTO Response Completo |
|----------------|-------------|--------------------|-----------------------|
| id | — | id | id |
| txid | — | txid | txid |
| idUsuario | — | — | idUsuario |
| tipo | tipo | — | tipo |
| metodo | metodo | — | metodo |
| status | — | — | status |
| valorSolicitacao | valor | — | valorSolicitado |
| valorPago | — | — | valorPago |
| copiaECola | — | copiaECola | — |
| transactionId | — | transactionId | — |
| dataCriacao | — | — | dataCriacao |
| dataExpiracao | — | dataExpiracao | dataExpiracao |
| dataFinalizada | — | — | dataFinalizada |

## Tratamento de Erros

- Validação ocorre automaticamente via `@Valid` nos parâmetros dos controllers
- Spring MVC lança `MethodArgumentNotValidException` para falhas de Bean Validation
- O GlobalExceptionHandler intercepta todas as exceções em ordem de especificidade
- Exceções específicas são tratadas antes da exceção genérica (ordem de declaração dos @ExceptionHandler)

## Correctness Properties

*Uma propriedade é uma característica ou comportamento que deve ser verdadeiro em todas as execuções válidas de um sistema — essencialmente, uma declaração formal sobre o que o sistema deve fazer.*

### Property 1: Validação rejeita valores não-positivos

*Para qualquer* BigDecimal que seja nulo, zero ou negativo, a validação de CobrancaRequestDTO SHALL produzir uma ConstraintViolation para o campo valor.

**Validates: Requirements 1.1**

### Property 2: Validação rejeita strings em branco

*Para qualquer* string composta apenas de caracteres whitespace (incluindo string vazia e nula), a validação de CheckoutValidateRequestDTO SHALL produzir ConstraintViolation para cada campo preenchido com tal string.

**Validates: Requirements 1.4**

### Property 3: Round-trip de serialização de PixWebhookDTO

*Para qualquer* PixWebhookDTO válido com lista de itens de tamanho arbitrário, serializar para JSON e desserializar de volta SHALL produzir um objeto equivalente ao original.

**Validates: Requirements 1.5**

### Property 4: Serialização de Response DTOs preserva todos os campos

*Para qualquer* instância válida de CobrancaCompletoResponseDTO com todos os campos preenchidos, a serialização JSON SHALL conter todos os 11 campos com valores correspondentes.

**Validates: Requirements 2.3, 2.4**

### Property 5: LockIndisponivelException preserva mensagem na resposta

*Para qualquer* mensagem String não-nula, o GlobalExceptionHandler ao tratar LockIndisponivelException SHALL retornar status 422 com codigo "LOCK_INDISPONIVEL" e a mensagem original preservada.

**Validates: Requirements 3.1**

### Property 6: Exceções genéricas sempre retornam resposta fixa

*Para qualquer* Exception não-mapeada (com qualquer mensagem), o GlobalExceptionHandler SHALL retornar status 500 com codigo "ERRO_INTERNO" e mensagem "Erro ao criar cobrança." independentemente da mensagem original.

**Validates: Requirements 3.3**

### Property 7: ErrorResponse serializa exatamente dois campos

*Para qualquer* par de strings (codigo, mensagem), a serialização JSON de ErrorResponse SHALL conter exatamente dois campos nomeados "codigo" e "mensagem".

**Validates: Requirements 3.5**

### Property 8: Round-trip de serialização de CobrancaEventDTO

*Para qualquer* CobrancaEventDTO válido, serializar para JSON e desserializar de volta SHALL produzir um objeto equivalente ao original.

**Validates: Requirements 4.1, 4.2**
