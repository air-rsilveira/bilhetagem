# Plano de Implementação: Lock Distribuído - LockService e LockExecutor

## Visão Geral

Implementação do mecanismo de lock distribuído via Redis (SET NX EX) com TTL de 5 segundos. O plano segue abordagem incremental: primeiro a exceção de negócio, depois a interface e implementação do LockService, depois o LockExecutor com garantia de cleanup no finally, e por fim os testes (incluindo o teste obrigatório #7 do desafio técnico).

## Tasks

- [x] 1. Criar exceção de negócio e interface do LockService
  - [x] 1.1 Criar LockIndisponivelException
    - Criar `src/main/java/com/v/challenge/exception/LockIndisponivelException.java`
    - Estender RuntimeException com construtor que recebe mensagem String
    - _Requirements: 3.1, 3.2_

  - [x] 1.2 Criar interface LockService
    - Criar `src/main/java/com/v/challenge/lock/LockService.java`
    - Definir método `boolean tryLock(String key, Duration ttl)`
    - Definir método `void unlock(String key)`
    - _Requirements: 1.1, 1.2_

- [x] 2. Implementar RedisLockService
  - [x] 2.1 Criar RedisLockService
    - Criar `src/main/java/com/v/challenge/lock/RedisLockService.java`
    - Anotar com @Service
    - Injetar StringRedisTemplate via construtor
    - Implementar tryLock usando `redisTemplate.opsForValue().setIfAbsent(key, "LOCKED", ttl)` com `Boolean.TRUE.equals()` para null-safety
    - Implementar unlock usando `redisTemplate.delete(key)`
    - _Requirements: 1.3, 1.4, 1.5, 1.6_

  - [ ]* 2.2 Escrever testes unitários para RedisLockService
    - Mockar StringRedisTemplate e ValueOperations com Mockito
    - Testar que tryLock chama setIfAbsent com key, "LOCKED" e Duration corretos
    - Testar que tryLock retorna true quando setIfAbsent retorna true
    - Testar que tryLock retorna false quando setIfAbsent retorna false ou null
    - Testar que unlock chama delete com a chave correta
    - _Requirements: 1.3, 1.4, 1.5_

- [x] 3. Implementar LockExecutor
  - [x] 3.1 Criar LockExecutor
    - Criar `src/main/java/com/v/challenge/lock/LockExecutor.java`
    - Anotar com @Component
    - Injetar LockService via construtor
    - Implementar `<T> T executeWithLock(String lockKey, Duration ttl, Supplier<T> supplier)`
    - Se tryLock retorna false: lançar LockIndisponivelException("Geração de cobrança em andamento.")
    - Se tryLock retorna true: executar supplier.get() no bloco try, unlock no bloco finally
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.3_

  - [x] 3.2 Escrever LockExecutorTest (Teste obrigatório #7)
    - Criar `src/test/java/com/v/challenge/lock/LockExecutorTest.java`
    - Usar Mockito para mockar LockService
    - Testar que executeWithLock executa supplier e retorna resultado quando lock adquirido
    - Testar que unlock é chamado no finally MESMO quando supplier lança exceção (teste obrigatório #7)
    - Testar que LockIndisponivelException é lançada com mensagem correta quando lock não disponível
    - Testar que unlock NÃO é chamado quando lock não foi adquirido
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [ ]* 3.3 Escrever property tests para LockExecutor com jqwik
    - **Property 1: Preservação do resultado do Supplier** — Para qualquer valor gerado, executeWithLock retorna o mesmo valor do supplier
    - **Property 2: Garantia de liberação do lock** — Para qualquer execução (sucesso ou exceção), unlock é chamado exatamente uma vez
    - **Property 3: Rejeição com mensagem correta** — Para qualquer chave/TTL com lock indisponível, exceção tem mensagem exata
    - **Property 4: Preservação de mensagem na exceção** — Para qualquer String, LockIndisponivelException.getMessage() preserva a mensagem
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 3.2, 3.3**

- [x] 4. Checkpoint - Garantir que todos os testes passam
  - Garantir que todos os testes passam, perguntar ao usuário se houver dúvidas.

## Notes

- Tasks marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido
- Cada task referencia requisitos específicos para rastreabilidade
- O teste 3.2 (LockExecutorTest) é o **teste obrigatório #7** do desafio técnico e NÃO é opcional
- Property tests usam jqwik 1.8.2 (já no pom.xml)
- Profile de teste exclui Redis autoconfiguration — todos os testes mockam LockService/StringRedisTemplate
- Linguagem de implementação: Java 17 com Spring Boot 3.2

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2", "3.1"] },
    { "id": 3, "tasks": ["3.2", "3.3"] }
  ]
}
```
