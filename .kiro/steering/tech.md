# Tech Stack

## Language & Framework

- **Java 17**
- **Spring Boot 3.2.0** (parent POM) — Web, Data JPA, Security, Validation, Actuator
- **Maven Wrapper** committed (`mvnw` / `mvnw.cmd`, `.mvn/wrapper/`); pins **Maven 3.9.16**. Prefer `./mvnw` over a local `mvn`. Requires `JAVA_HOME` set to a JDK 17+.

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
./mvnw spring-boot:run

# Health check
curl http://localhost:8080/actuator/health

# Run tests + coverage check
./mvnw clean verify

# Build the jar (skip tests)
./mvnw package -DskipTests

# Build & run the full stack in Docker
docker-compose -f docker/docker-compose.yml up --build
```

Coverage report is generated at `target/site/jacoco/index.html`.
