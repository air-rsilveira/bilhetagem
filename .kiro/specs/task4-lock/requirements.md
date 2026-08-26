# Documento de Requisitos

## Introdução

Implementação de lock distribuído via Redis para o microserviço de bilhetagem. O mecanismo utiliza o padrão SET NX EX do Redis para garantir que operações críticas (como criação de cobrança) sejam executadas com exclusão mútua por usuário. O componente LockExecutor orquestra a aquisição e liberação do lock com garantia de cleanup no bloco finally, prevenindo deadlocks.

## Glossário

- **LockService**: Interface que define o contrato para aquisição e liberação de locks distribuídos
- **RedisLockService**: Implementação concreta de LockService que utiliza StringRedisTemplate do Spring Data Redis com comando SET NX EX
- **LockExecutor**: Componente orquestrador que encapsula o padrão try/finally para garantir liberação do lock mesmo em caso de exceção
- **LockIndisponivelException**: Exceção de negócio lançada quando o lock não pode ser adquirido, indicando operação concorrente em andamento
- **SET_NX_EX**: Comando Redis que seta uma chave somente se ela não existir (NX) com tempo de expiração (EX), implementando lock atômico
- **TTL**: Time-To-Live de 5 segundos configurado no lock para auto-expiração em caso de falha na liberação explícita

## Requisitos

### Requisito 1

**User Story:** Como microserviço, quero adquirir locks distribuídos via Redis, para que operações críticas por usuário sejam executadas com exclusão mútua.

#### Critérios de Aceitação

1. THE LockService SHALL definir o método tryLock que recebe uma chave String e uma Duration de TTL e retorna boolean indicando sucesso na aquisição
2. THE LockService SHALL definir o método unlock que recebe uma chave String e libera o lock associado
3. WHEN tryLock é invocado com uma chave que não existe no Redis, THEN THE RedisLockService SHALL executar SET NX EX com valor "LOCKED" e a Duration fornecida como TTL, e retornar true
4. WHEN tryLock é invocado com uma chave que já existe no Redis, THEN THE RedisLockService SHALL retornar false sem modificar a chave existente
5. WHEN unlock é invocado com uma chave, THEN THE RedisLockService SHALL executar DELETE na chave do Redis
6. THE RedisLockService SHALL utilizar StringRedisTemplate do Spring Data Redis para comunicação com o Redis

### Requisito 2

**User Story:** Como microserviço, quero que a execução de operações com lock tenha garantia de liberação do lock, para que deadlocks sejam prevenidos mesmo em caso de exceção.

#### Critérios de Aceitação

1. WHEN LockExecutor.executeWithLock é invocado e o lock é adquirido com sucesso, THEN THE LockExecutor SHALL executar o Supplier fornecido e retornar seu resultado
2. WHEN LockExecutor.executeWithLock é invocado e o lock é adquirido com sucesso, THEN THE LockExecutor SHALL liberar o lock no bloco finally após a execução do Supplier, independentemente de sucesso ou exceção
3. WHEN o Supplier lança uma exceção durante a execução, THEN THE LockExecutor SHALL liberar o lock antes de propagar a exceção
4. WHEN LockExecutor.executeWithLock é invocado e o lock não pode ser adquirido, THEN THE LockExecutor SHALL lançar LockIndisponivelException com a mensagem "Geração de cobrança em andamento."
5. THE LockExecutor SHALL ser genérico (tipo parametrizado T) para suportar qualquer tipo de retorno do Supplier

### Requisito 3

**User Story:** Como microserviço, quero uma exceção de negócio específica para lock indisponível, para que a camada de controle possa retornar uma resposta HTTP adequada ao cliente.

#### Critérios de Aceitação

1. THE LockIndisponivelException SHALL estender RuntimeException
2. THE LockIndisponivelException SHALL aceitar uma mensagem String no construtor e repassá-la à superclasse
3. WHEN o lock não é adquirido, THEN THE LockExecutor SHALL lançar LockIndisponivelException com mensagem exata "Geração de cobrança em andamento."
