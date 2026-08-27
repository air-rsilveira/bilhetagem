# Documento de Requisitos

## Introdução

Este documento especifica os requisitos para o scaffold do projeto de microserviço de cobranças de bilhetagem e sua infraestrutura Docker. O objetivo é criar a base do projeto Spring Boot com todas as dependências, configurações de profiles, estrutura de pacotes e Docker Compose funcional para desenvolvimento e demonstração.

## Glossário

- **Projeto_Maven**: Projeto Java gerenciado pelo Apache Maven com arquivo `pom.xml` contendo dependências e configurações de build.
- **Docker_Compose**: Ferramenta de orquestração de containers que define e executa múltiplos serviços a partir de um arquivo `docker-compose.yml`.
- **Dockerfile**: Arquivo de instruções para construção de imagem Docker da aplicação com build multi-stage.
- **Application_YML**: Arquivo de configuração Spring Boot (`application.yml`) que define propriedades por profile de execução.
- **Profile_Default**: Configuração padrão da aplicação que conecta a PostgreSQL, Redis e Kafka reais via Docker.
- **Profile_Test**: Configuração de testes que utiliza banco H2 in-memory e mocks para dependências externas.
- **Health_Check**: Endpoint do Spring Boot Actuator (`/actuator/health`) que reporta o status de conectividade com componentes externos.
- **Estrutura_Pacotes**: Organização de pacotes Java seguindo responsabilidades: controller, service, repository, domain, dto, integration, lock, exception, security, event.

## Requisitos

### Requisito 1: Projeto Maven Compilável

**User Story:** Como desenvolvedor, quero um projeto Maven com todas as dependências configuradas, para que eu possa compilar e executar a aplicação sem erros.

#### Critérios de Aceite

1. THE Projeto_Maven SHALL compilar sem erros utilizando Java 17 e Spring Boot 3.
2. THE Projeto_Maven SHALL incluir as dependências spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, spring-kafka, spring-boot-starter-data-redis e spring-boot-starter-actuator.
3. THE Projeto_Maven SHALL incluir a dependência postgresql com escopo runtime.
4. THE Projeto_Maven SHALL incluir as dependências h2, spring-boot-starter-test, testcontainers junit-jupiter e testcontainers postgresql com escopo test.
5. THE Projeto_Maven SHALL incluir a dependência lombok para redução de boilerplate.
6. THE Projeto_Maven SHALL definir a versão do Java como 17 nas propriedades do Maven.

### Requisito 2: Estrutura de Pacotes

**User Story:** Como desenvolvedor, quero uma estrutura de pacotes organizada por responsabilidade, para que o código seja fácil de navegar e manter.

#### Critérios de Aceite

1. THE Estrutura_Pacotes SHALL conter os pacotes controller, service, service.strategy, repository, domain, dto, integration, lock, exception, security e event dentro de `com.v.challenge`.
2. THE Estrutura_Pacotes SHALL conter a classe principal da aplicação Spring Boot no pacote raiz `com.v.challenge`.
3. THE Estrutura_Pacotes SHALL conter o diretório de testes espelhando a estrutura principal em `src/test/java/com/v/challenge`.

### Requisito 3: Infraestrutura Docker Compose

**User Story:** Como desenvolvedor, quero um Docker Compose funcional com toda a infraestrutura necessária, para que eu possa iniciar o ambiente de desenvolvimento com um único comando.

#### Critérios de Aceite

1. WHEN o comando `docker-compose up` é executado, THE Docker_Compose SHALL iniciar os serviços PostgreSQL, Redis, Kafka, Zookeeper e a aplicação sem erros.
2. THE Docker_Compose SHALL expor o PostgreSQL na porta 5432 com database `cobrancas` criado automaticamente.
3. THE Docker_Compose SHALL expor o Redis na porta 6379.
4. THE Docker_Compose SHALL expor o Kafka na porta 9092 com Zookeeper como dependência.
5. THE Docker_Compose SHALL expor a aplicação na porta 8080 com Profile_Default ativo.
6. THE Docker_Compose SHALL definir dependências entre serviços para garantir ordem de inicialização correta (aplicação depende de PostgreSQL, Redis e Kafka).
7. THE Docker_Compose SHALL utilizar variáveis de ambiente para configurar credenciais e conexões dos serviços.

### Requisito 4: Dockerfile Multi-Stage

**User Story:** Como desenvolvedor, quero um Dockerfile otimizado com build multi-stage, para que a imagem final seja leve e segura.

#### Critérios de Aceite

1. THE Dockerfile SHALL utilizar build multi-stage com estágio de compilação Maven e estágio de execução JRE.
2. THE Dockerfile SHALL produzir uma imagem final baseada em JRE 17 slim ou alpine.
3. THE Dockerfile SHALL expor a porta 8080 para a aplicação.
4. THE Dockerfile SHALL definir o JAR gerado como entrypoint da imagem.

### Requisito 5: Configuração de Profiles

**User Story:** Como desenvolvedor, quero profiles separados para desenvolvimento e teste, para que eu possa executar testes isolados sem dependências externas.

#### Critérios de Aceite

1. WHILE o Profile_Default está ativo, THE Application_YML SHALL configurar conexão com PostgreSQL na URL `jdbc:postgresql://localhost:5432/cobrancas`.
2. WHILE o Profile_Default está ativo, THE Application_YML SHALL configurar conexão com Redis no host localhost porta 6379.
3. WHILE o Profile_Default está ativo, THE Application_YML SHALL configurar Kafka bootstrap-servers em `localhost:9092`.
4. WHILE o Profile_Test está ativo, THE Application_YML SHALL configurar banco H2 in-memory com URL `jdbc:h2:mem:testdb`.
5. WHILE o Profile_Test está ativo, THE Application_YML SHALL desabilitar autoconfiguração de Redis e Kafka.
6. THE Application_YML SHALL configurar o endpoint `/actuator/health` como público e incluir detalhes de componentes.

### Requisito 6: Health Check e Conectividade

**User Story:** Como operador, quero verificar o status de todos os componentes via health check, para que eu possa monitorar a saúde da aplicação.

#### Critérios de Aceite

1. WHEN o endpoint `/actuator/health` é acessado, THE Health_Check SHALL retornar status UP quando todos os componentes estão conectados.
2. WHEN o PostgreSQL está acessível, THE Health_Check SHALL incluir indicador de database com status UP.
3. WHEN o Redis está acessível, THE Health_Check SHALL incluir indicador de Redis com status UP.
4. IF o PostgreSQL não está acessível, THEN THE Health_Check SHALL retornar status DOWN com detalhes do componente afetado.

### Requisito 7: Schema do Banco de Dados

**User Story:** Como desenvolvedor, quero o schema SQL da tabela de cobranças definido, para que a estrutura de dados esteja pronta para uso.

#### Critérios de Aceite

1. THE Projeto_Maven SHALL incluir arquivo `schema.sql` com DDL da tabela `cobranca` contendo todas as colunas necessárias para o domínio.
2. WHEN a aplicação inicia com Profile_Default, THE Application_YML SHALL executar o schema.sql para inicialização do banco.
3. WHEN a aplicação inicia com Profile_Test, THE Application_YML SHALL utilizar JPA auto-DDL para criação automática das tabelas.
