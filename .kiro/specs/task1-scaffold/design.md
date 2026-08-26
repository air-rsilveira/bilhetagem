# Documento de Design: Scaffold do Projeto e Infraestrutura Docker

## Visão Geral da Arquitetura

O scaffold estabelece a base do microserviço de cobranças de bilhetagem como um projeto Spring Boot 3 com Java 17, organizado em pacotes por responsabilidade. A infraestrutura é orquestrada via Docker Compose com PostgreSQL, Redis, Kafka e a própria aplicação.

```
┌─────────────────────────────────────────────────────────────────┐
│                    Docker Compose                                 │
│                                                                   │
│  ┌──────────┐  ┌───────┐  ┌─────────────────┐  ┌────────────┐ │
│  │PostgreSQL│  │ Redis │  │ Kafka + Zookeeper│  │ Aplicação  │ │
│  │  :5432   │  │ :6379 │  │     :9092       │  │   :8080    │ │
│  └──────────┘  └───────┘  └─────────────────┘  └────────────┘ │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Componentes

### 1. Projeto Maven (pom.xml)

Arquivo de configuração do projeto com Spring Boot 3 parent, Java 17 e todas as dependências necessárias.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.v</groupId>
    <artifactId>challenge</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>bilhetagem</name>
    <description>Microserviço de Cobranças de Bilhetagem</description>

    <properties>
        <java.version>17</java.version>
        <testcontainers.version>1.19.3</testcontainers.version>
    </properties>

    <dependencies>
        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Persistência -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Segurança -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Messaging -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- Cache/Lock -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Utilitários -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Testes -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2. Classe Principal da Aplicação

```java
package com.v.challenge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BilhetagemApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilhetagemApplication.class, args);
    }
}
```

### 3. Estrutura de Pacotes

```
src/main/java/com/v/challenge/
├── BilhetagemApplication.java
├── controller/
│   └── .gitkeep
├── service/
│   └── strategy/
│       └── .gitkeep
├── repository/
│   └── .gitkeep
├── domain/
│   └── .gitkeep
├── dto/
│   └── .gitkeep
├── integration/
│   └── .gitkeep
├── lock/
│   └── .gitkeep
├── exception/
│   └── .gitkeep
├── security/
│   └── .gitkeep
└── event/
    └── .gitkeep
```

Cada pacote conterá um arquivo `.gitkeep` ou uma classe placeholder para garantir que a estrutura seja versionada no Git.

### 4. Configuração de Profiles (application.yml)

```yaml
# application.yml - Profile Default
spring:
  application:
    name: bilhetagem
  datasource:
    url: jdbc:postgresql://localhost:5432/cobrancas
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
      show-components: always

server:
  port: 8080
```

```yaml
# application-test.yml - Profile Test
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
  sql:
    init:
      mode: never
```

### 5. Docker Compose

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: bilhetagem-postgres
    environment:
      POSTGRES_DB: cobrancas
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: bilhetagem-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: bilhetagem-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: bilhetagem-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 10s
      retries: 5

  app:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: bilhetagem-app
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: default
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/cobrancas
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_DATA_REDIS_HOST: redis
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy

volumes:
  postgres_data:
```

### 6. Dockerfile Multi-Stage

```dockerfile
# Estágio 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Estágio 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 7. Schema SQL

```sql
CREATE TABLE IF NOT EXISTS cobranca (
    id BIGSERIAL PRIMARY KEY,
    id_usuario VARCHAR(255) NOT NULL,
    nome_solicitante VARCHAR(255) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    metodo VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    valor_solicitacao NUMERIC(19,2),
    valor_pago NUMERIC(19,2),
    txid VARCHAR(255),
    copia_e_cola TEXT,
    transaction_id VARCHAR(255),
    acs_url VARCHAR(500),
    three_ds_payload TEXT,
    data_criacao TIMESTAMP NOT NULL,
    data_expiracao TIMESTAMP,
    data_finalizada TIMESTAMP,
    id_cobranca_origem BIGINT REFERENCES cobranca(id)
);

CREATE INDEX idx_cobranca_txid ON cobranca(txid);
CREATE INDEX idx_cobranca_transaction_id ON cobranca(transaction_id);
CREATE INDEX idx_cobranca_id_usuario ON cobranca(id_usuario);
CREATE INDEX idx_cobranca_id_origem ON cobranca(id_cobranca_origem);
```

### 8. Configuração de Segurança (Placeholder)

Para permitir acesso ao actuator health sem autenticação durante o scaffold:

```java
package com.v.challenge.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

## Interfaces

### Health Check Response

```java
// GET /actuator/health - Resposta esperada
{
    "status": "UP",
    "components": {
        "db": {
            "status": "UP",
            "details": {
                "database": "PostgreSQL",
                "validationQuery": "isValid()"
            }
        },
        "redis": {
            "status": "UP",
            "details": {
                "version": "7.x"
            }
        }
    }
}
```

## Modelo de Dados

A tabela `cobranca` é a entidade central do sistema. O schema inclui:
- Campos de identificação: `id`, `id_usuario`, `nome_solicitante`
- Campos de classificação: `tipo`, `metodo`, `status` (enums)
- Campos financeiros: `valor_solicitacao`, `valor_pago`
- Campos PIX: `txid`, `copia_e_cola`
- Campos Cartão: `transaction_id`, `acs_url`, `three_ds_payload`
- Campos temporais: `data_criacao`, `data_expiracao`, `data_finalizada`
- Versionamento: `id_cobranca_origem` (auto-referência)

## Tratamento de Erros

Nesta fase de scaffold, os erros são tratados pelo Spring Boot de forma padrão:
- Falha de conexão com banco: aplicação não inicia (fail-fast)
- Falha de conexão com Redis: health check reporta DOWN
- Falha de conexão com Kafka: health check reporta DOWN

## Decisões de Design

1. **H2 para testes**: Elimina dependência de infraestrutura externa durante testes unitários
2. **Exclusão de autoconfiguration no profile test**: Evita erros de conexão com Redis/Kafka durante testes
3. **Schema.sql separado**: DDL explícito permite controle preciso da estrutura do banco
4. **Docker Compose com healthchecks**: Garante que a aplicação só inicia quando as dependências estão prontas
5. **Multi-stage Dockerfile**: Imagem final leve (~200MB) contendo apenas JRE e JAR
6. **.gitkeep em pacotes vazios**: Garante que a estrutura é versionada mesmo sem classes implementadas

## Correctness Properties

*Este módulo é predominantemente de infraestrutura e configuração (IaC/scaffold). As verificações de corretude são baseadas em testes de integração e smoke tests, não em property-based testing.*

*Análise da prework indica que todos os critérios de aceite são classificados como SMOKE ou INTEGRATION. Isso é esperado para uma task de scaffold que cria configurações estáticas (pom.xml, docker-compose.yml, Dockerfile, application.yml) e verifica conectividade com serviços externos.*

### Estratégia de Teste

- **Smoke Tests**: Verificar compilação Maven, existência de arquivos e estrutura de pacotes
- **Integration Tests**: Verificar que o Spring context carrega com profile test (H2) e que health checks reportam status correto com Testcontainers

*Nenhuma propriedade universal (property-based test) é aplicável nesta task, pois não há lógica de negócio com variação de input.*
