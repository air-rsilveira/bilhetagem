# Tech Stack

## Language & Framework

- **Java 17**
- **Spring Boot 3.2.0** (parent POM) — Web, Data JPA, Security, Validation, Actuator
- **Maven** build (no wrapper committed; use a local `mvn` 3.9+)

## Libraries & Infrastructure

- **PostgreSQL 15** — persistence (runtime); **H2** in tests
- **Redis 7** — distributed lock (`SET NX EX` pattern)
- **Apache Kafka** (Confluent 7.5.0) via **spring-kafka** — status-change events
- **Lombok** — boilerplate (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`); excluded from the packaged jar
- **Docker / Docker Compose** — local infra (Postgres, Redis, Kafka/Zookeeper, app)

## Testing

- **JUnit 5** + **Spring Boot Test**
- **jqwik 1.8.2** — property-based testing
- **Testcontainers** (junit-jupiter, postgresql) — available for DB-faithful tests
- **JaCoCo 0.8.11** — coverage; **verify** enforces a **minimum 70% line coverage** on package `com.v.challenge.service`

## Profiles

- `default` (`application.yml`) — connects to local Postgres/Redis/Kafka.
- `test` (`application-test.yml`) — H2 in memory, uses `NoOpLockService` and `NoOpCobrancaEventPublisher`, so tests run **without Docker**.

## Common Commands

```bash
# Start infra (Postgres, Redis, Kafka)
docker-compose -f docker/docker-compose.yml up -d

# Run the app (default profile, port 8080)
mvn spring-boot:run

# Health check
curl http://localhost:8080/actuator/health

# Run tests + coverage check
mvn clean verify

# Build the jar (skip tests)
mvn package -DskipTests

# Build & run the full stack in Docker
docker-compose -f docker/docker-compose.yml up --build
```

Coverage report is generated at `target/site/jacoco/index.html`.
