# Plano de Implementação: Scaffold do Projeto e Infraestrutura Docker

## Visão Geral

Criar o projeto Spring Boot 3 com Java 17, estrutura de pacotes completa, configurações de profiles, Docker Compose com toda infraestrutura e Dockerfile multi-stage. Ao final, o projeto compila sem erros e `docker-compose up` levanta o ambiente completo.

## Tasks

- [x] 1. Criar projeto Maven com dependências
  - [x] 1.1 Criar `pom.xml` com Spring Boot 3 parent, Java 17 e todas as dependências
    - Incluir spring-boot-starter-web, data-jpa, security, kafka, data-redis, actuator
    - Incluir postgresql (runtime), h2 (test), lombok, spring-boot-starter-test
    - Incluir testcontainers junit-jupiter e testcontainers postgresql (test)
    - Configurar testcontainers BOM para gerenciamento de versão
    - Configurar spring-boot-maven-plugin excluindo lombok
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [x] 1.2 Criar classe principal `BilhetagemApplication.java`
    - Criar em `src/main/java/com/v/challenge/BilhetagemApplication.java`
    - Anotar com `@SpringBootApplication`
    - Implementar método `main` com `SpringApplication.run`
    - _Requirements: 2.2_

  - [x] 1.3 Criar estrutura de pacotes com placeholders
    - Criar diretórios: controller, service, service/strategy, repository, domain, dto, integration, lock, exception, security, event
    - Adicionar `.gitkeep` em cada pacote vazio para versionamento
    - Criar diretório de testes: `src/test/java/com/v/challenge/`
    - _Requirements: 2.1, 2.3_

- [x] 2. Configurar profiles da aplicação
  - [x] 2.1 Criar `application.yml` com configuração default
    - Configurar datasource PostgreSQL: `jdbc:postgresql://localhost:5432/cobrancas`
    - Configurar Redis: host localhost, porta 6379
    - Configurar Kafka: bootstrap-servers `localhost:9092` com serializers JSON
    - Configurar Actuator: expor health e info, show-details always
    - Configurar `spring.sql.init.mode: always` com `schema-locations`
    - Configurar JPA com `ddl-auto: none` e dialeto PostgreSQL
    - _Requirements: 5.1, 5.2, 5.3, 5.6_

  - [x] 2.2 Criar `application-test.yml` com configuração de teste
    - Configurar H2 in-memory: `jdbc:h2:mem:testdb`
    - Excluir autoconfiguration de Redis e Kafka
    - Configurar `spring.sql.init.mode: never`
    - Configurar JPA com `ddl-auto: create-drop`
    - _Requirements: 5.4, 5.5_

  - [x] 2.3 Criar `schema.sql` com DDL da tabela cobranca
    - Criar tabela `cobranca` com todas as colunas (id, id_usuario, nome_solicitante, tipo, metodo, status, valores, campos PIX, campos cartão, datas, id_cobranca_origem)
    - Criar índices para txid, transaction_id, id_usuario e id_cobranca_origem
    - Usar tipos adequados: BIGSERIAL, VARCHAR, NUMERIC(19,2), TEXT, TIMESTAMP
    - _Requirements: 7.1, 7.2, 7.3_

- [x] 3. Configurar segurança básica
  - [x] 3.1 Criar `SecurityConfig.java` com permissões do actuator
    - Criar em `src/main/java/com/v/challenge/security/SecurityConfig.java`
    - Permitir acesso público a `/actuator/**`
    - Exigir autenticação para demais endpoints
    - Desabilitar CSRF (API REST)
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 4. Criar infraestrutura Docker
  - [x] 4.1 Criar `docker/docker-compose.yml` com todos os serviços
    - Definir serviço PostgreSQL (postgres:15-alpine, porta 5432, database cobrancas, healthcheck)
    - Definir serviço Redis (redis:7-alpine, porta 6379, healthcheck)
    - Definir serviço Zookeeper (confluentinc/cp-zookeeper:7.5.0)
    - Definir serviço Kafka (confluentinc/cp-kafka:7.5.0, porta 9092, depends_on zookeeper, healthcheck)
    - Definir serviço app (build do Dockerfile, porta 8080, profile default, depends_on com condition service_healthy)
    - Configurar variáveis de ambiente para conexão entre serviços (hosts internos Docker)
    - Definir volume persistente para PostgreSQL
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

  - [x] 4.2 Criar `docker/Dockerfile` com build multi-stage
    - Estágio 1 (build): maven:3.9-eclipse-temurin-17, copiar pom.xml, baixar dependências offline, copiar src, executar `mvn package -DskipTests`
    - Estágio 2 (runtime): eclipse-temurin:17-jre-alpine, copiar JAR, EXPOSE 8080, ENTRYPOINT java -jar
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 5. Checkpoint - Verificar compilação e estrutura
  - Executar `mvn compile` e verificar que compila sem erros
  - Verificar que todos os arquivos foram criados nos locais corretos
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Teste de integração do contexto Spring
  - [x] 6.1 Criar teste de carga do contexto Spring com profile test
    - Criar `src/test/java/com/v/challenge/BilhetagemApplicationTests.java`
    - Verificar que o contexto Spring carrega com sucesso usando profile test (H2)
    - Anotar com `@SpringBootTest` e `@ActiveProfiles("test")`
    - _Requirements: 5.4, 5.5, 6.1_

- [x] 7. Final checkpoint - Validar projeto completo
  - Executar `mvn clean verify` com profile test
  - Verificar que testes passam
  - Verificar que estrutura de pacotes está completa
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Este é um scaffold de infraestrutura — não há property-based tests pois não há lógica de negócio com variação de input
- A verificação principal é que o projeto compila e o Spring context carrega com profile test
- O Docker Compose deve ser testado manualmente pelo desenvolvedor (`docker-compose up` no diretório docker/)
- Tarefas subsequentes (Tasks 2-11 do refinamento) irão preencher os pacotes com implementações reais
- O `SecurityConfig` criado aqui é um placeholder que será substituído pelo JWT Filter na Task 3

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["2.1", "2.2", "2.3"] },
    { "id": 3, "tasks": ["3.1", "4.1", "4.2"] },
    { "id": 4, "tasks": ["6.1"] }
  ]
}
```
