# Bilhetagem — Microserviço de Cobranças

Microserviço responsável pela criação, acompanhamento e gestão do ciclo de vida de cobranças (cobranças) de um sistema de bilhetagem. Suporta cobranças via **PIX** e **cartão de crédito**, com trilha de auditoria completa por versionamento, lock distribuído por usuário e publicação de eventos a cada transição de status.

## Tecnologias

- **Java 17**
- **Spring Boot 3.2** (Web, Data JPA, Security, Actuator)
- **PostgreSQL 15** — persistência
- **Redis 7** — lock distribuído (padrão `SET NX EX`)
- **Apache Kafka** (Confluent 7.5.0) — eventos de mudança de status
- **Docker / Docker Compose** — infraestrutura local
- **Maven Wrapper** (`./mvnw`) — build, sem necessidade de instalar o Maven
- **Testes**: JUnit 5, H2, Testcontainers, jqwik (property-based testing), JaCoCo (cobertura)

## Como Executar

### Pré-requisitos

- Docker e Docker Compose
- Java 17+ (JDK) com a variável `JAVA_HOME` configurada

> O projeto inclui o **Maven Wrapper**. Use `./mvnw` (Linux/macOS) ou `mvnw.cmd` (Windows) — não é preciso instalar o Maven. Na primeira execução o wrapper baixa o Maven 3.9.16 automaticamente.

### 1. Subir a infraestrutura (PostgreSQL, Redis, Kafka)

```bash
docker-compose -f docker/docker-compose.yml up -d
```

### 2. Rodar a aplicação (profile default, conecta na infra local)

```bash
./mvnw spring-boot:run
```

A aplicação sobe na porta **8080**. Health check:

```bash
curl http://localhost:8080/actuator/health
```

### 3. Rodar os testes (com verificação de cobertura JaCoCo)

```bash
./mvnw clean verify
```

O profile de teste usa **H2 em memória** e exclui Redis/Kafka, portanto os testes rodam **sem necessidade de Docker**.

### Alternativa: subir a stack completa via Docker

```bash
docker-compose -f docker/docker-compose.yml up --build
```

## Autenticação (JWT mockado)

Todos os endpoints (exceto o webhook PIX e o Actuator) exigem um token **JWT Bearer** válido.

O JWT é **mockado**: a aplicação **não** possui endpoint de login/emissão de token — ela apenas **valida** o token recebido, usando uma **chave HMAC-SHA256 fixa hardcoded** (`bilhetagem-secret-key-for-testing-purposes-only-32bytes!`). A validação checa a assinatura e o `exp` (expiração). As claims lidas são: `sub`, `given_name`, `family_name`, `cpf`, `exp`.

Como a chave é fixa, **um token com `exp` longo é fixo e reutilizável** — você pode gerá-lo uma vez e usar sempre nos testes. Para gerar um token, assine um JWT HS256 com a chave acima (ex.: [jwt.io](https://jwt.io) ou o utilitário `JwtTokenUtil` dos testes do projeto).

Token de **teste** pronto para uso (claims `sub=user-123`, `Maria Silva`, `cpf=12345678900`, válido até 2035-01-01):

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyIsImdpdmVuX25hbWUiOiJNYXJpYSIsImZhbWlseV9uYW1lIjoiU2lsdmEiLCJjcGYiOiIxMjM0NTY3ODkwMCIsImV4cCI6MjA1MTIyMjQwMH0.hSXEJwhSGYKmBcwFzCXw-1khqFw2MxlLSE5VAe-9Jp0
```

> Este token serve apenas para desenvolvimento/teste. **Não use em produção.**

### Collection do Postman

Há uma collection pronta em `postman/bilhetagem.postman_collection.json` com chamadas de exemplo para todos os endpoints. Ela já vem com o token de teste acima na variável de collection `jwt`, então basta importar e executar. Para usar outras claims, substitua o valor da variável `jwt` por um token gerado com a mesma chave.

## Endpoints

Base path: `/api/v1/cobrancas`

### POST `/api/v1/cobrancas` — Criar cobrança

```bash
curl -X POST http://localhost:8080/api/v1/cobrancas \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "valor": 100.00,
    "tipo": "RECARGA",
    "metodo": "PIX"
  }'
```

- `valor` (obrigatório, positivo)
- `tipo` (opcional, default `RECARGA`): `RECARGA`, `RECARGA_TERCEIROS`, `ENVIO_CARTAO`
- `metodo` (opcional, default `PIX`): `PIX`, `CARTAO_CREDITO`

Resposta: **201 Created** com dados básicos da cobrança (id, txid, copiaECola, dataExpiracao, transactionId).

### GET `/api/v1/cobrancas/{id}` — Consultar cobrança

```bash
curl -X GET http://localhost:8080/api/v1/cobrancas/1 \
  -H "Authorization: Bearer <JWT>"
```

Resposta: **200 OK** com os dados completos. Para cobranças PIX em status consultável, o status é reconciliado automaticamente com a consulta externa (criando uma nova versão quando há mudança).

### POST `/api/v1/cobrancas/webhook/pix` — Webhook de pagamento PIX

Endpoint **público** (sem autenticação), consumido pelo provedor PIX.

```bash
curl -X POST http://localhost:8080/api/v1/cobrancas/webhook/pix \
  -H "Content-Type: application/json" \
  -d '{
    "pix": [
      {
        "txid": "PIX-EXEMPLO-123",
        "horario": "2024-01-01T10:00:00Z",
        "valor": 100.00
      }
    ]
  }'
```

Resposta: **200 OK**. Cada item confirmado gera uma nova versão da cobrança com status `FINALIZADA`.

### POST `/api/v1/cobrancas/{transactionId}/validate` — Validar checkout 3DS (cartão)

```bash
curl -X POST http://localhost:8080/api/v1/cobrancas/TXN-123/validate \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "cavv": "AAABBBCCC",
    "xid": "XID-123",
    "eci": "05"
  }'
```

Resposta: **200 OK**. Cobrança é atualizada para `FINALIZADA` (aprovado) ou `ERRO_APROVACAO_PEDIDO`.

## Premissas Adotadas

- **JWT mockado**: os tokens são assinados/validados com uma chave simétrica **HMAC-SHA256 fixa** (hardcoded para dev/teste). Não é adequado para produção — veja Trade-offs.
- **Clients externos são mocks/fakes**: o gateway de pagamento, a validação de checkout (3DS) e a consulta de status externo são implementações simuladas, sem chamadas de rede reais.
- **Versionamento na mesma tabela**: cada mudança de status cria uma **nova linha** na tabela `cobranca`, apontando para o registro original via `idCobrancaOrigem`.
- **Timezone `America/Sao_Paulo`**: todas as datas (criação, expiração, finalização) usam esse fuso.
- **Implementações NoOp para o profile `test`**: `NoOpLockService` e `NoOpCobrancaEventPublisher` permitem rodar os testes sem Redis nem Kafka.

## Trade-offs Documentados

- **Versionamento — tabela única vs histórico separado**: optou-se por manter todas as versões na mesma tabela `cobranca` (ligadas por `idCobrancaOrigem`). Ganho em **simplicidade** e consultas de auditoria triviais; a longo prazo, uma tabela de histórico dedicada teria melhor **performance** e menor crescimento da tabela principal.
- **Lock distribuído — Redis `SET NX EX` vs banco**: escolhido Redis pelo baixo custo e alta **performance** do lock com TTL (5s), evitando contenção no banco. A alternativa em banco (lock pessimista/advisory lock) simplificaria a infraestrutura, mas acopla o lock à carga transacional do PostgreSQL.
- **Testes de integração com H2 vs Testcontainers**: os testes end-to-end rodam com **H2 em memória**, permitindo execução rápida e **sem Docker** em qualquer ambiente/CI. Testcontainers (PostgreSQL) está disponível como dependência e é o caminho de **evolução** para testes fiéis ao banco de produção.

## Estrutura do Projeto

```
bilhetagem/
├── docker/                                  # docker-compose.yml + Dockerfile
├── docs/                                    # tasks.md (refinamento técnico)
├── src/
│   ├── main/
│   │   ├── java/com/v/challenge/
│   │   │   ├── BilhetagemApplication.java   # Entry point Spring Boot
│   │   │   ├── controller/                  # Endpoints REST
│   │   │   ├── service/                     # Regras de negócio
│   │   │   │   └── strategy/                # Strategy: criação por método (PIX/Cartão)
│   │   │   ├── domain/                      # Entidades JPA e enums
│   │   │   ├── repository/                  # Repositórios Spring Data JPA
│   │   │   ├── dto/                         # DTOs de request/response (records)
│   │   │   ├── event/                       # Publicação de eventos Kafka
│   │   │   ├── integration/                 # Clients externos (mocks)
│   │   │   ├── lock/                        # Lock distribuído (Redis)
│   │   │   ├── exception/                   # Exceções + @ControllerAdvice
│   │   │   └── security/                    # Filtro JWT, SecurityConfig, UserContext
│   │   └── resources/
│   │       ├── application.yml              # Profile default
│   │       ├── application-test.yml         # Profile test (H2, sem Redis/Kafka)
│   │       └── schema.sql                   # DDL da tabela cobranca
│   └── test/java/com/v/challenge/           # Testes unitários e de integração
├── pom.xml
└── README.md
```

## Testes Obrigatórios

Os 7 testes end-to-end obrigatórios estão em
`src/test/java/com/v/challenge/integration/CobrancaIntegrationTest.java`:

1. `deveCriarEConsultarCobrancaPix` — fluxo completo POST → GET de uma cobrança PIX.
2. `deveRetornar401SemToken` — endpoint protegido rejeita requisição sem token.
3. `deveRetornar401ComTokenInvalido` — endpoint protegido rejeita token malformado.
4. `deveProcessarWebhookPixEFinalizarCobranca` — webhook PIX cria nova versão `FINALIZADA`.
5. `deveRetornar400ParaRequestInvalido` — validação de request (valor negativo) retorna 400.
6. `webhookDeveSerAcessivelSemAutenticacao` — webhook é público (sem JWT).
7. Cobertura complementar de lock distribuído, versionamento e validação 3DS nos testes de
   `service`, `lock` e `security` (ex.: `CobrancaServiceTest`, `LockExecutorTest`, `JwtAuthenticationFilterTest`).

### Cobertura (JaCoCo)

A cobertura é verificada na fase `verify`, com **mínimo de 70% de linhas** no pacote
`com.v.challenge.service`. O relatório HTML é gerado em `target/site/jacoco/index.html`.
