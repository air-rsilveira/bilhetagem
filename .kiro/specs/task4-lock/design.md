# Documento de Design

## Introdução

Este documento descreve a arquitetura e implementação do mecanismo de lock distribuído via Redis para o microserviço de bilhetagem. A solução utiliza o padrão SET NX EX do Redis para garantir exclusão mútua em operações críticas (criação de cobrança por usuário), com um orquestrador que garante liberação do lock via bloco finally.

## Arquitetura

### Visão Geral

```
┌──────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Service    │────▶│  LockExecutor   │────▶│   LockService   │
│  (Cobrança)  │     │  try/finally    │     │  (interface)    │
└──────────────┘     └─────────────────┘     └─────────────────┘
                              │                        │
                              │                        ▼
                              │               ┌─────────────────┐
                              │               │ RedisLockService│
                              │               │ (SET NX EX)     │
                              │               └─────────────────┘
                              │                        │
                              ▼                        ▼
                     ┌─────────────────┐     ┌─────────────────┐
                     │ LockIndisponivel│     │     Redis       │
                     │ Exception       │     │  (porta 6379)   │
                     └─────────────────┘     └─────────────────┘
```

### Fluxo de Execução com Lock

1. Service invoca `LockExecutor.executeWithLock(lockKey, ttl, supplier)`
2. LockExecutor chama `LockService.tryLock(lockKey, ttl)`
3. RedisLockService executa `SET lockKey "LOCKED" NX EX ttlSeconds` via StringRedisTemplate
4. Se lock adquirido (true): executa supplier no bloco try, libera lock no finally
5. Se lock não adquirido (false): lança `LockIndisponivelException` imediatamente
6. Em caso de exceção do supplier: lock é liberado no finally antes de propagar a exceção

### Diagrama de Sequência (Sucesso)

```
Service          LockExecutor        LockService(Redis)
  │                   │                      │
  │ executeWithLock() │                      │
  │──────────────────▶│                      │
  │                   │ tryLock(key, 5s)     │
  │                   │─────────────────────▶│
  │                   │        true          │
  │                   │◀─────────────────────│
  │                   │                      │
  │                   │ supplier.get()       │
  │                   │──────┐               │
  │                   │◀─────┘ resultado     │
  │                   │                      │
  │                   │ [finally] unlock(key)│
  │                   │─────────────────────▶│
  │                   │                      │
  │    resultado      │                      │
  │◀──────────────────│                      │
```

## Componentes

### 1. LockService (Interface)

**Arquivo:** `src/main/java/com/v/challenge/lock/LockService.java`

```java
package com.v.challenge.lock;

import java.time.Duration;

public interface LockService {
    boolean tryLock(String key, Duration ttl);
    void unlock(String key);
}
```

### 2. RedisLockService (Implementação)

**Arquivo:** `src/main/java/com/v/challenge/lock/RedisLockService.java`

```java
package com.v.challenge.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisLockService implements LockService {

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String key, Duration ttl) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(key, "LOCKED", ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock(String key) {
        redisTemplate.delete(key);
    }
}
```

**Decisões:**
- `Boolean.TRUE.equals(result)` trata null-safety (Redis pode retornar null)
- Valor fixo "LOCKED" — não é necessário identificar o owner neste contexto
- TTL é passado diretamente ao `setIfAbsent` para garantir atomicidade (SET NX EX num único comando)

### 3. LockExecutor (Orquestrador)

**Arquivo:** `src/main/java/com/v/challenge/lock/LockExecutor.java`

```java
package com.v.challenge.lock;

import com.v.challenge.exception.LockIndisponivelException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class LockExecutor {

    private final LockService lockService;

    public LockExecutor(LockService lockService) {
        this.lockService = lockService;
    }

    public <T> T executeWithLock(String lockKey, Duration ttl, Supplier<T> supplier) {
        if (!lockService.tryLock(lockKey, ttl)) {
            throw new LockIndisponivelException("Geração de cobrança em andamento.");
        }

        try {
            return supplier.get();
        } finally {
            lockService.unlock(lockKey);
        }
    }
}
```

**Decisões:**
- Tipo genérico `<T>` permite retornar qualquer tipo do supplier
- `throw` antes do try — se lock falha, não há recurso a liberar
- `finally` garante unlock mesmo com exceção do supplier — este é o teste obrigatório #7

### 4. LockIndisponivelException (Exceção de Negócio)

**Arquivo:** `src/main/java/com/v/challenge/exception/LockIndisponivelException.java`

```java
package com.v.challenge.exception;

public class LockIndisponivelException extends RuntimeException {

    public LockIndisponivelException(String message) {
        super(message);
    }
}
```

## Modelo de Dados

### Lock no Redis

| Campo | Valor | Descrição |
|-------|-------|-----------|
| Key | `lock:cobranca:{idUsuario}` | Padrão de chave (definido pelo chamador) |
| Value | `"LOCKED"` | Valor fixo, sem identificação de owner |
| TTL | 5 segundos | Auto-expiração para prevenir deadlocks |

### Parâmetros do LockExecutor

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| lockKey | String | Chave do lock no Redis |
| ttl | Duration | Tempo de expiração do lock (5s padrão) |
| supplier | Supplier<T> | Operação a executar com exclusão mútua |

## Tratamento de Erros

| Cenário | Comportamento | Exceção |
|---------|--------------|---------|
| Lock não adquirido (chave já existe) | Lança exceção imediatamente | LockIndisponivelException("Geração de cobrança em andamento.") |
| Supplier lança exceção | Libera lock no finally, propaga exceção original | Exceção original do supplier |
| Redis indisponível (tryLock) | StringRedisTemplate propaga exceção | RedisConnectionException (Spring) |
| Redis indisponível (unlock) | Exceção no finally pode mascarar exceção do supplier | RedisConnectionException (Spring) |

## Decisões de Design

1. **Interface + Implementação**: LockService como interface permite substituir a implementação em testes (mock) sem depender de Redis real
2. **SET NX EX atômico**: Um único comando Redis garante que a verificação de existência e a criação com TTL são atômicas
3. **TTL de 5 segundos**: Previne deadlock se o processo falhar antes de unlock — lock expira automaticamente
4. **Sem identificação de owner**: Para simplicidade, não verificamos se quem faz unlock é o mesmo que fez lock (aceitável para operações curtas < TTL)
5. **Test profile exclui Redis**: Testes usam mock do LockService, sem necessidade de Redis real ou Testcontainers para testes unitários do LockExecutor
6. **RuntimeException**: LockIndisponivelException é unchecked para não poluir assinaturas de método com throws

## Estratégia de Testes

- **LockExecutorTest (Teste obrigatório #7)**: Usa Mockito para mockar LockService. Verifica:
  - Execução bem-sucedida com retorno correto
  - Garantia de unlock no finally com exceção do supplier (teste obrigatório)
  - LockIndisponivelException quando lock não é adquirido
- **RedisLockServiceTest**: Usa Mockito para mockar StringRedisTemplate. Verifica chamadas corretas ao Redis.
- **Property tests com jqwik**: Validam propriedades universais do LockExecutor independente de tipo de retorno.

## Correctness Properties

*Uma propriedade é uma característica ou comportamento que deve ser verdadeiro em todas as execuções válidas do sistema — essencialmente, uma declaração formal sobre o que o sistema deve fazer.*

### Property 1: Preservação do resultado do Supplier

*Para qualquer* valor de tipo T retornado por um Supplier, quando o lock é adquirido com sucesso, LockExecutor.executeWithLock deve retornar exatamente o mesmo valor que o Supplier produz.

**Validates: Requirements 2.1**

### Property 2: Garantia de liberação do lock (try/finally)

*Para qualquer* execução onde o lock é adquirido com sucesso, independentemente de o Supplier retornar normalmente ou lançar qualquer exceção, LockService.unlock deve ser invocado exatamente uma vez com a mesma chave usada no tryLock.

**Validates: Requirements 2.2, 2.3**

### Property 3: Rejeição com mensagem correta quando lock indisponível

*Para qualquer* chave e TTL onde tryLock retorna false, LockExecutor.executeWithLock deve lançar LockIndisponivelException cuja getMessage() retorna exatamente "Geração de cobrança em andamento."

**Validates: Requirements 2.4, 3.3**

### Property 4: Preservação de mensagem na exceção

*Para qualquer* String mensagem passada ao construtor de LockIndisponivelException, getMessage() deve retornar exatamente a mesma String.

**Validates: Requirements 3.2**
