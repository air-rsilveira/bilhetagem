# Tech Stack

## Core

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.0
- **Build**: Maven (pom.xml)
- **Packaging**: JAR (Spring Boot plugin)

## Dependencies

| Category | Library |
|----------|---------|
| Web | spring-boot-starter-web |
| Persistence | spring-boot-starter-data-jpa, PostgreSQL driver |
| Security | spring-boot-starter-security (custom JWT filter, no OAuth) |
| Messaging | spring-kafka |
| Cache/Lock | spring-boot-starter-data-redis |
| Monitoring | spring-boot-starter-actuator |
| Utilities | Lombok |
| Test | spring-boot-starter-test, H2, Testcontainers (PostgreSQL), jqwik 1.8.2 |

## Infrastructure (Docker Compose)

- PostgreSQL 15 (port 5432, db: `cobrancas`)
- Redis 7 (port 6379)
- Kafka + Zookeeper (Confluent 7.5.0, port 9092)
- Application (port 8080)

## Profiles

- **default**: Connects to PostgreSQL, Redis, Kafka (local Docker infra)
- **test**: H2 in-memory, Redis and Kafka auto-config excluded

## Common Commands

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run tests
./mvnw test

# Run with test profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=test

# Start infrastructure
docker-compose -f docker/docker-compose.yml up -d

# Start full stack (app + infra)
docker-compose -f docker/docker-compose.yml up --build

# Health check
curl http://localhost:8080/actuator/health
```

## Key Conventions

- JPA ddl-auto is `none` in production; schema managed via `schema.sql`
- JWT secret is hardcoded for dev/testing (not production-ready)
- Kafka topic: `cobrancas.status-alterado`, partitioned by `idUsuario`
- Redis used exclusively for distributed locking (SET NX EX pattern, 5s TTL)
- Actuator and webhook endpoints are public; all others require a valid JWT Bearer token
