# Project Structure

```
bilhetagem/
├── docker/
│   ├── docker-compose.yml    # Full infra: PostgreSQL, Redis, Kafka, App
│   └── Dockerfile            # Multi-stage build for the application
├── docs/
│   └── tasks.md              # Technical refinement / task breakdown
├── src/
│   ├── main/
│   │   ├── java/com/v/challenge/
│   │   │   ├── BilhetagemApplication.java   # Spring Boot entry point
│   │   │   ├── controller/                  # REST API endpoints
│   │   │   ├── service/                     # Business logic
│   │   │   │   └── strategy/               # Strategy pattern for charge creation (PIX vs Cartão)
│   │   │   ├── domain/                      # JPA entities and enums
│   │   │   ├── repository/                  # Spring Data JPA repositories
│   │   │   ├── dto/                         # Request/Response DTOs (records)
│   │   │   ├── event/                       # Kafka event publishing
│   │   │   ├── integration/                 # External service clients (mocked)
│   │   │   ├── lock/                        # Distributed lock (Redis-based)
│   │   │   ├── exception/                   # Custom exceptions + @ControllerAdvice handler
│   │   │   └── security/                    # JWT filter, SecurityConfig, UserContext
│   │   └── resources/
│   │       ├── application.yml              # Default profile config
│   │       ├── application-test.yml         # Test profile (H2, no Redis/Kafka)
│   │       └── schema.sql                   # DDL for cobranca table
│   └── test/
│       └── java/com/v/challenge/            # Unit and integration tests
├── pom.xml                                  # Maven build descriptor
└── README.md
```

## Architecture Patterns

- **Layered architecture**: Controller → Service → Repository
- **Strategy pattern**: `CobrancaCriacaoStrategy` interface with implementations per payment method (PIX, Cartão)
- **Strategy registry**: Auto-discovers strategy beans and maps them by `CobrancaMetodoEnum`
- **Distributed lock**: `LockService` → `RedisLockService`, orchestrated by `LockExecutor` with guaranteed unlock in `finally`
- **Event-driven**: `CobrancaEventPublisher` interface decouples business logic from Kafka implementation
- **ThreadLocal user context**: `UserContextHolder` stores authenticated user info extracted from JWT for the request lifecycle
- **Versioning via new rows**: Status changes create new `Cobranca` records linked via `idCobrancaOrigem` for audit

## Naming Conventions

- Package root: `com.v.challenge`
- Entities: singular nouns (`Cobranca`)
- Enums: `<Entity><Concept>Enum` (e.g., `CobrancaStatusEnum`)
- DTOs: `<Entity><Purpose>DTO` (e.g., `CobrancaRequestDTO`, `CobrancaBasicoResponseDTO`)
- Strategies: `<Method>CriacaoStrategy`
- Exceptions: descriptive name + `Exception` suffix
- REST base path: `/api/v1/cobrancas`
