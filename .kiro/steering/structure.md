# Project Structure

## Layout

```
bilhetagem/
├── .mvn/wrapper/          # Maven Wrapper (maven-wrapper.jar + properties)
├── mvnw / mvnw.cmd        # Maven Wrapper scripts (use these to build)
├── docker/                # docker-compose.yml + Dockerfile
├── docs/                  # tasks.md (technical refinement notes)
├── src/
│   ├── main/
│   │   ├── java/com/v/challenge/
│   │   │   ├── BilhetagemApplication.java  # Spring Boot entry point
│   │   │   ├── controller/                 # REST endpoints
│   │   │   ├── service/                    # Business rules
│   │   │   │   └── strategy/                # Creation strategy per method (PIX/Cartão)
│   │   │   ├── domain/                      # JPA entities + enums
│   │   │   ├── repository/                  # Spring Data JPA repositories
│   │   │   ├── dto/                         # Request/response DTOs (records)
│   │   │   ├── event/                       # Kafka event publishing
│   │   │   ├── integration/                 # External clients (mocked)
│   │   │   ├── lock/                        # Distributed lock (Redis)
│   │   │   ├── exception/                   # Exceptions + @ControllerAdvice
│   │   │   └── security/                    # JWT filter, SecurityConfig, UserContext
│   │   └── resources/
│   │       ├── application.yml              # default profile
│   │       ├── application-test.yml         # test profile (H2, no Redis/Kafka)
│   │       └── schema.sql                   # DDL for the cobranca table
│   └── test/java/com/v/challenge/           # unit + integration tests
├── pom.xml
└── README.md
```

## Package base

All Java code lives under `com.v.challenge` (groupId `com.v`, artifactId `challenge`).

## Layered architecture

`controller` → `service` → `repository`, with `integration`, `lock`, and `event` as collaborators injected into the service. Keep controllers thin (validation + delegation); business logic belongs in `service`.

## Conventions

- **Domain language is Portuguese.** Class names, methods, fields, and enum values follow the business vocabulary (e.g. `Cobranca`, `criarCobranca`, `valorSolicitacao`, `CobrancaStatusEnum`). Match this when adding code.
- **DTOs are Java `record`s** in the `dto` package (request/response). Use them at the API boundary; do not expose JPA entities directly. Bean Validation (`@NotNull`, `@Positive`) goes on request records.
- **Constructor injection via Lombok** `@RequiredArgsConstructor` with `private final` fields. Do not use field injection.
- **Services** are `@Service @Transactional @Slf4j`. Use `@Slf4j` for logging.
- **Enums** are persisted with `@Enumerated(EnumType.STRING)`.
- **Timestamps**: always build with `LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))`.

## Key patterns

- **Strategy pattern** for charge creation by method: `CobrancaCriacaoStrategy` implementations (`PixCriacaoStrategy`, `CartaoCriacaoStrategy`) resolved through `CobrancaCriacaoStrategyRegistry`. Add a new payment method by adding a strategy + registering it.
- **Versioning**: never mutate history destructively. On a status change, clone via `criarNovaVersao(...)` (copies properties except `id`/`dataCriacao`, sets `idCobrancaOrigem`) and save a new row. Query the latest state with `findVersaoMaisRecente`.
- **Distributed lock**: wrap user-scoped mutations in `lockExecutor.executeWithLock(key, ttl, ...)` with key `"cobrancas:" + idUsuario`.
- **Events**: publish `publicarCobrancaCriada` / `publicarStatusAlterado` after persisting a change.
- **Pluggable infra**: real (`RedisLockService`, `KafkaCobrancaEventPublisher`) vs NoOp (`NoOpLockService`, `NoOpCobrancaEventPublisher`) implementations selected by profile.

## Testing conventions

- Integration tests live in `src/test/.../integration/` (e.g. `CobrancaIntegrationTest`); unit tests sit alongside their package (`service`, `lock`, `security`).
- Maintain **≥70% line coverage** in `com.v.challenge.service` (enforced by JaCoCo on `verify`).
