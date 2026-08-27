# 📋 Refinamento Técnico - Microserviço de Cobranças de Bilhetagem

## Visão Geral

Implementar microserviço de cobranças de bilhetagem com lock distribuído, estratégias de criação por método de pagamento (PIX/Cartão), webhook PIX, validação 3DS e publicação de eventos. Sistema contempla versionamento de cobranças para auditoria completa e consulta de status externo para reprocessamento.

## Stack/Tecnologias

- **Backend**: Java 17+, Spring Boot 3, Maven
- **Persistência**: JPA/Hibernate, PostgreSQL (produção), H2 (testes)  
- **Cache/Lock**: Redis via Docker (lock distribuído)
- **Messaging**: Apache Kafka via Docker (eventos de mudança de status)
- **Segurança**: JWT mockado com filter customizado
- **Testes**: JUnit 5, Mockito, Testcontainers
- **Infraestrutura**: Docker Compose completo

## Arquivos Impactados

```
src/
├── main/java/com/v/challenge/
│   ├── controller/
│   │   └── CobrancaController.java — REST endpoints
│   ├── service/
│   │   ├── CobrancaService.java — business logic principal  
│   │   └── strategy/
│   │       ├── CobrancaCriacaoStrategy.java — interface strategy
│   │       ├── PixCriacaoStrategy.java — implementação PIX
│   │       ├── CartaoCriacaoStrategy.java — implementação cartão
│   │       └── CobrancaCriacaoStrategyRegistry.java — registry das strategies
│   ├── repository/
│   │   └── CobrancaRepository.java — JPA repository
│   ├── domain/
│   │   ├── Cobranca.java — entidade principal
│   │   ├── CobrancaTipoEnum.java
│   │   ├── CobrancaMetodoEnum.java  
│   │   └── CobrancaStatusEnum.java
│   ├── dto/
│   │   ├── CobrancaRequestDTO.java
│   │   ├── CobrancaBasicoResponseDTO.java
│   │   ├── CobrancaCompletoResponseDTO.java
│   │   ├── PixWebhookDTO.java
│   │   ├── CheckoutValidateRequestDTO.java
│   │   └── CobrancaEventDTO.java — eventos Kafka
│   ├── integration/
│   │   ├── PagamentoGatewayClient.java — mock gateway pagamentos
│   │   ├── CheckoutValidationClient.java — mock validação 3DS
│   │   └── StatusConsultaExternaClient.java — mock consulta status
│   ├── lock/
│   │   ├── LockService.java — interface lock distribuído
│   │   ├── RedisLockService.java — implementação Redis
│   │   └── LockExecutor.java — executor com try/finally
│   ├── exception/
│   │   ├── LockIndisponivelException.java
│   │   ├── CobrancaNaoEncontradaException.java
│   │   └── GlobalExceptionHandler.java — @ControllerAdvice
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java — filter JWT
│   │   ├── UserContext.java — dados usuário autenticado
│   │   └── UserContextHolder.java — ThreadLocal holder
│   └── event/
│       ├── CobrancaEventPublisher.java — interface eventos
│       └── KafkaCobrancaEventPublisher.java — implementação Kafka
├── test/java/com/v/challenge/
│   ├── service/
│   │   └── CobrancaServiceTest.java — testes unitários obrigatórios
│   ├── lock/
│   │   └── LockExecutorTest.java — teste try/finally
│   └── integration/
│       └── CobrancaIntegrationTest.java — fluxos completos
├── resources/
│   ├── application.yml — configuração profiles
│   └── schema.sql — DDL da tabela
├── docker/
│   ├── Dockerfile — build da aplicação
│   └── docker-compose.yml — infra completa
└── README.md — documentação execução + trade-offs
```

---
## Task 1: Scaffold do Projeto e Infraestrutura Docker

**Descrição:** Criar projeto Spring Boot com todas dependências e Docker Compose funcional para desenvolvimento e demonstração. Configurar profiles para teste (H2) e produção (PostgreSQL+Redis+Kafka).

**Arquivos:** `pom.xml`, `docker-compose.yml`, `Dockerfile`, `application.yml`

**Dependências Maven:**
```xml
<!-- Core Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Banco de dados -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>

<!-- Utilitários -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>

<!-- Testes -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
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
```

**Docker Compose Services:**
- PostgreSQL (porta 5432, database `cobranças`)
- Redis (porta 6379)  
- Kafka + Zookeeper (porta 9092)
- Aplicação (porta 8080, profile default)

**Cenários de Teste:**
- ✅ `docker-compose up` levanta todos os services sem erro
- ✅ Aplicação responde em `http://localhost:8080/actuator/health`
- ✅ Profile test usa H2 in-memory
- ✅ Profile default conecta no PostgreSQL do compose

**Critérios de Aceite:**
- [ ] Projeto Maven compila sem erros
- [ ] Docker Compose levanta infra completa
- [ ] Aplicação conecta em PostgreSQL, Redis e Kafka
- [ ] Health check retorna UP para todos componentes

---
## Task 2: Domínio - Entidade e Enums

**Descrição:** Modelar entidade `Cobranca` com todos campos obrigatórios do teste + campo `idCobrancaOrigem` para versionamento. Criar enums com codes específicos e repository JPA com queries customizadas para busca por txid, transactionId e versionamento.

**Arquivos:** `src/main/java/com/v/challenge/domain/`

**Estrutura da Entidade:**
```java
@Entity
@Table(name = "cobranca")  
public class Cobranca {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String idUsuario;
    
    @Column(nullable = false) 
    private String nomeSolicitante;
    
    @Enumerated(EnumType.STRING)
    private CobrancaTipoEnum tipo;
    
    @Enumerated(EnumType.STRING)
    private CobrancaMetodoEnum metodo;
    
    @Enumerated(EnumType.STRING)
    private CobrancaStatusEnum status;
    
    private BigDecimal valorSolicitacao;
    private BigDecimal valorPago;
    
    private String txid;
    private String copiaECola;
    private String transactionId;
    private String acsUrl;
    
    @Column(columnDefinition = "TEXT")
    private String threeDsPayload;
    
    private LocalDateTime dataCriacao;
    private LocalDateTime dataExpiracao;
    private LocalDateTime dataFinalizada;
    
    // Campo para versionamento
    private Long idCobrancaOrigem;
}
```

**Repository Queries:**
```java
public interface CobrancaRepository extends JpaRepository<Cobranca, Long> {
    
    // Busca versão mais recente de uma cobrança (original ou filha)
    @Query("SELECT c FROM Cobranca c WHERE (c.idCobrancaOrigem = :id OR c.id = :id) ORDER BY c.dataCriacao DESC")
    Optional<Cobranca> findVersaoMaisRecente(@Param("id") Long id);
    
    // Busca por txid (PIX)
    Optional<Cobranca> findTopByTxidOrderByDataCriacaoDesc(String txid);
    
    // Busca por transactionId (Cartão)  
    Optional<Cobranca> findByTransactionId(String transactionId);
}
```

**Cenários de Teste:**
- ✅ Enums retornam codes corretos (SOLICITADA=2, FINALIZADA=5, etc.)
- ✅ Repository persiste e consulta entidade corretamente
- ✅ Query `findVersaoMaisRecente` retorna versão filha quando existe
- ✅ Busca por txid e transactionId funcionam

**Critérios de Aceite:**
- [ ] Entidade mapeada com todos campos obrigatórios
- [ ] Enums implementados com codes corretos  
- [ ] Repository com queries customizadas funcionais
- [ ] Testes unitários passam para enums e repository

---
## Task 3: Autenticação - JWT Filter e UserContext

**Descrição:** Implementar filtro JWT que extrai dados do token (mockado) e disponibiliza via ThreadLocal para os services. Configurar Spring Security para aceitar tokens Bearer e rejeitar requisições sem token com 401.

**Arquivos:** `src/main/java/com/v/challenge/security/`

**UserContext Structure:**
```java
public record UserContext(
    String idUsuario,
    String givenName, 
    String familyName,
    String cpf
) {
    public String getNomeCompleto() {
        return givenName + " " + familyName;
    }
}
```

**JWT Filter Implementation:**
```java
@Component  
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
                                  
        String token = extractTokenFromHeader(request);
        if (token != null) {
            try {
                UserContext userContext = parseTokenToUserContext(token);
                UserContextHolder.setContext(userContext);
                
                // Popula SecurityContext para Spring Security
                Authentication auth = new UsernamePasswordAuthenticationToken(
                    userContext.idUsuario(), null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
                
            } catch (Exception e) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        } else {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
```

**Token de Teste (Base64 encoded):**
```json
{
  "sub": "user-123",
  "given_name": "João",
  "family_name": "Silva", 
  "cpf": "12345678901",
  "iat": 1640995200,
  "exp": 1640998800
}
```

**Cenários de Teste:**
- ✅ Request com JWT válido extrai UserContext corretamente
- ✅ Request sem header Authorization retorna 401
- ✅ Request com token inválido retorna 401
- ✅ UserContext fica disponível no ThreadLocal durante request

**Critérios de Aceite:**
- [ ] Filter extrai claims do JWT mockado
- [ ] UserContextHolder funcional com ThreadLocal
- [ ] Spring Security configurado com filter customizado
- [ ] Requests sem token retornam 401 Unauthorized

---
## Task 4: Lock Distribuído - LockService e LockExecutor

**Descrição:** Implementar lock distribuído via Redis usando comando SET NX EX com TTL de 5 segundos. Criar LockExecutor que garante liberação do lock no finally mesmo com exceções. Implementar exceção de negócio quando lock não disponível.

**Arquivos:** `src/main/java/com/v/challenge/lock/`

**Interface e Implementação:**
```java
public interface LockService {
    boolean tryLock(String key, Duration ttl);
    void unlock(String key);
}

@Service
public class RedisLockService implements LockService {
    
    private final StringRedisTemplate redisTemplate;
    
    @Override
    public boolean tryLock(String key, Duration ttl) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(key, "LOCKED", ttl);
        return Boolean.TRUE.equals(result);
    }
    
    @Override  
    public void unlock(String key) {
        redisTemplate.delete(key);
    }
}
```

**LockExecutor with Try/Finally:**
```java
@Component
public class LockExecutor {
    
    private final LockService lockService;
    
    public <T> T executeWithLock(String lockKey, Duration ttl, Supplier<T> supplier) {
        if (!lockService.tryLock(lockKey, ttl)) {
            throw new LockIndisponivelException("Geração de cobrança em andamento.");
        }
        
        try {
            return supplier.get();
        } finally {
            lockService.unlock(lockKey);
        }
    }
}
```

**Exception de Negócio:**
```java
public class LockIndisponivelException extends RuntimeException {
    public LockIndisponivelException(String message) {
        super(message);
    }
}
```

**Cenários de Teste:**
- ✅ LockExecutor executa supplier e libera lock com sucesso
- ✅ LockExecutor garante unlock no finally mesmo com exceção do supplier (teste obrigatório #7)
- ✅ LockExecutor lança LockIndisponivelException quando lock não disponível
- ✅ Redis recebe comandos SET NX EX corretos

**Critérios de Aceite:**
- [ ] Lock distribuído funcional via Redis SET NX EX
- [ ] LockExecutor garante cleanup no finally
- [ ] Exceção de negócio com mensagem correta
- [ ] TTL de 5 segundos respeitado

---
## Task 5: DTOs e Exception Handler

**Descrição:** Criar DTOs de request/response para todos endpoints e tratamento global de exceções com @ControllerAdvice. Mapear exceções de negócio para status HTTP corretos e mensagens padronizadas.

**Arquivos:** `src/main/java/com/v/challenge/dto/`, `src/main/java/com/v/challenge/exception/`

**Request DTOs:**
```java
public record CobrancaRequestDTO(
    @NotNull @Positive BigDecimal valor,
    CobrancaTipoEnum tipo,  // default RECARGA se null
    CobrancaMetodoEnum metodo  // default PIX se null
) {}

public record PixWebhookDTO(
    List<PixWebhookItemDTO> pix
) {}

public record PixWebhookItemDTO(
    String txid,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'") 
    LocalDateTime horario,
    BigDecimal valor
) {}

public record CheckoutValidateRequestDTO(
    @NotBlank String cavv,
    @NotBlank String xid, 
    @NotBlank String eci
) {}
```

**Response DTOs:**  
```java
public record CobrancaBasicoResponseDTO(
    Long id,
    String txid,
    String copiaECola, 
    LocalDateTime dataExpiracao,
    String transactionId
) {}

public record CobrancaCompletoResponseDTO(
    Long id,
    String txid,
    String idUsuario,
    CobrancaTipoEnum tipo,
    CobrancaMetodoEnum metodo,
    CobrancaStatusEnum status,
    BigDecimal valorSolicitado,
    BigDecimal valorPago,
    LocalDateTime dataCriacao,
    LocalDateTime dataExpiracao,
    LocalDateTime dataFinalizada
) {}
```

**Global Exception Handler:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(LockIndisponivelException.class)
    public ResponseEntity<ErrorResponse> handleLockIndisponivel(LockIndisponivelException ex) {
        return ResponseEntity.status(422)
            .body(new ErrorResponse("LOCK_INDISPONIVEL", ex.getMessage()));
    }
    
    @ExceptionHandler(CobrancaNaoEncontradaException.class) 
    public ResponseEntity<ErrorResponse> handleCobrancaNaoEncontrada(CobrancaNaoEncontradaException ex) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("COBRANCA_NAO_ENCONTRADA", "Cobrança não encontrada"));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return ResponseEntity.status(500)
            .body(new ErrorResponse("ERRO_INTERNO", "Erro ao criar cobrança."));
    }
}

public record ErrorResponse(String codigo, String mensagem) {}
```

**Cenários de Teste:**
- ✅ LockIndisponivelException retorna 422 com mensagem correta
- ✅ CobrancaNaoEncontradaException retorna 404
- ✅ Exception genérica retorna 500 com "Erro ao criar cobrança."
- ✅ Bean Validation falha retorna 400 com detalhes

**Critérios de Aceite:**
- [ ] DTOs com Bean Validation funcionais
- [ ] Exception Handler mapeia erros para status HTTP corretos
- [ ] Mensagens de erro padronizadas
- [ ] Serialização JSON funcional

---
## Task 6: Strategy - Criação de Cobrança por Método

**Descrição:** Implementar padrão Strategy para criação de cobranças PIX vs Cartão de Crédito. Cada strategy chama clients externos mockaveis e preenche campos específicos da cobrança conforme método de pagamento.

**Arquivos:** `src/main/java/com/v/challenge/service/strategy/`, `src/main/java/com/v/challenge/integration/`

**Strategy Interface:**
```java
public interface CobrancaCriacaoStrategy {
    Cobranca executar(Cobranca cobranca);
}
```

**PIX Strategy:**
```java
@Component
public class PixCriacaoStrategy implements CobrancaCriacaoStrategy {
    
    private final PagamentoGatewayClient gatewayClient;
    
    @Override
    public Cobranca executar(Cobranca cobranca) {
        PixCriacaoResponse response = gatewayClient.criarPix(
            cobranca.getValorSolicitacao(), 
            cobranca.getIdUsuario()
        );
        
        cobranca.setTxid(response.txid());
        cobranca.setCopiaECola(response.copiaECola());
        cobranca.setDataExpiracao(response.dataExpiracao());
        
        return cobranca;
    }
}
```

**Cartão Strategy:**
```java
@Component  
public class CartaoCriacaoStrategy implements CobrancaCriacaoStrategy {
    
    private final PagamentoGatewayClient gatewayClient;
    
    @Override
    public Cobranca executar(Cobranca cobranca) {
        CartaoTransacaoResponse response = gatewayClient.iniciarTransacaoCartao(
            cobranca.getValorSolicitacao(),
            cobranca.getIdUsuario()
        );
        
        cobranca.setTransactionId(response.transactionId());
        // Opcionalmente pode vir dados 3DS
        if (response.requires3ds()) {
            cobranca.setAcsUrl(response.acsUrl());
            cobranca.setThreeDsPayload(response.threeDsPayload());
        }
        
        return cobranca;
    }
}
```

**Strategy Registry:**
```java
@Component
public class CobrancaCriacaoStrategyRegistry {
    
    private final Map<CobrancaMetodoEnum, CobrancaCriacaoStrategy> strategies;
    
    public CobrancaCriacaoStrategyRegistry(List<CobrancaCriacaoStrategy> strategies) {
        this.strategies = strategies.stream()
            .collect(Collectors.toMap(this::resolveMetodo, Function.identity()));
    }
    
    public CobrancaCriacaoStrategy getStrategy(CobrancaMetodoEnum metodo) {
        CobrancaCriacaoStrategy strategy = strategies.get(metodo);
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy não encontrada para método: " + metodo);
        }
        return strategy;
    }
}
```

**Mock Clients:**
```java
@Component
public class PagamentoGatewayClient {
    
    public PixCriacaoResponse criarPix(BigDecimal valor, String idUsuario) {
        return new PixCriacaoResponse(
            "PIX" + System.currentTimeMillis(),
            "00020126580014BR.GOV.BCB.PIX...",
            LocalDateTime.now().plusHours(2)
        );
    }
    
    public CartaoTransacaoResponse iniciarTransacaoCartao(BigDecimal valor, String idUsuario) {
        return new CartaoTransacaoResponse(
            "TXN" + System.currentTimeMillis(),
            false,  // requires3ds 
            null,   // acsUrl
            null    // threeDsPayload
        );
    }
}
```

**Cenários de Teste:**
- ✅ PixCriacaoStrategy preenche txid, copiaECola e dataExpiracao
- ✅ CartaoCriacaoStrategy preenche transactionId
- ✅ Registry resolve strategy correta por CobrancaMetodoEnum
- ✅ Registry lança exceção para método não suportado

**Critérios de Aceite:**
- [ ] Padrão Strategy implementado corretamente
- [ ] Cada strategy preenche campos específicos
- [ ] Registry funcional com injeção automática
- [ ] Mock clients retornam dados realistas

---
## Task 7: Kafka - Publicação de Eventos

**Descrição:** Implementar publicação de eventos Kafka a cada mudança de status de cobrança. Criar publisher abstrato e implementação Kafka para desacoplamento. Configurar tópico e serialização JSON.

**Arquivos:** `src/main/java/com/v/challenge/event/`

**Event DTO:**
```java
public record CobrancaEventDTO(
    Long cobrancaId,
    String idUsuario,
    CobrancaStatusEnum statusAtual,
    CobrancaStatusEnum statusAnterior,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timestamp,
    String eventoTipo  // "CRIADA", "STATUS_ALTERADO", "FINALIZADA"
) {}
```

**Publisher Interface:**
```java  
public interface CobrancaEventPublisher {
    void publicarCobrancaCriada(Cobranca cobranca);
    void publicarStatusAlterado(Cobranca cobranca, CobrancaStatusEnum statusAnterior);
}
```

**Kafka Implementation:**
```java
@Component
public class KafkaCobrancaEventPublisher implements CobrancaEventPublisher {
    
    private final KafkaTemplate<String, CobrancaEventDTO> kafkaTemplate;
    private static final String TOPIC = "cobrancas.status-alterado";
    
    @Override
    public void publicarCobrancaCriada(Cobranca cobranca) {
        CobrancaEventDTO event = new CobrancaEventDTO(
            cobranca.getId(),
            cobranca.getIdUsuario(), 
            cobranca.getStatus(),
            null,
            LocalDateTime.now(),
            "CRIADA"
        );
        
        kafkaTemplate.send(TOPIC, cobranca.getIdUsuario(), event);
    }
    
    @Override
    public void publicarStatusAlterado(Cobranca cobranca, CobrancaStatusEnum statusAnterior) {
        CobrancaEventDTO event = new CobrancaEventDTO(
            cobranca.getId(),
            cobranca.getIdUsuario(),
            cobranca.getStatus(), 
            statusAnterior,
            LocalDateTime.now(),
            "STATUS_ALTERADO"
        );
        
        kafkaTemplate.send(TOPIC, cobranca.getIdUsuario(), event);
    }
}
```

**Kafka Configuration:**
```yaml
spring:
  kafka:
    producer:
      bootstrap-servers: localhost:9092
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    topic:
      cobrancas-status-alterado:
        name: cobrancas.status-alterado
        partitions: 3
        replication-factor: 1
```

**Cenários de Teste:**
- ✅ Event publicado na criação da cobrança com eventoTipo "CRIADA"
- ✅ Event publicado na mudança de status com statusAnterior preenchido  
- ✅ Particionamento por idUsuario (key) funcionando
- ✅ Serialização JSON do evento correta

**Critérios de Aceite:**
- [ ] Publisher interface implementada
- [ ] Eventos publicados em momentos corretos
- [ ] Tópico Kafka configurado
- [ ] Particionamento por usuário funcional

---
## Task 8: CobrancaService - Criação de Cobrança (POST)

**Descrição:** Implementar fluxo completo de criação de cobrança com lock distribuído por usuário, aplicação da strategy conforme método, persistência e publicação de evento. Tratar exceções genéricas como erro de negócio conforme especificação.

**Arquivos:** `src/main/java/com/v/challenge/service/CobrancaService.java`

**Service Implementation:**
```java
@Service
@Transactional
public class CobrancaService {
    
    private final CobrancaRepository repository;
    private final LockExecutor lockExecutor; 
    private final CobrancaCriacaoStrategyRegistry strategyRegistry;
    private final CobrancaEventPublisher eventPublisher;
    
    public CobrancaBasicoResponseDTO criarCobranca(CobrancaRequestDTO request) {
        UserContext userContext = UserContextHolder.getContext();
        String lockKey = "cobrancas:" + userContext.idUsuario();
        
        try {
            return lockExecutor.executeWithLock(lockKey, Duration.ofSeconds(5), () -> {
                // Criar entidade com dados básicos
                Cobranca cobranca = new Cobranca();
                cobranca.setIdUsuario(userContext.idUsuario());
                cobranca.setNomeSolicitante(userContext.getNomeCompleto());
                cobranca.setValorSolicitacao(request.valor());
                cobranca.setTipo(request.tipo() != null ? request.tipo() : CobrancaTipoEnum.RECARGA);
                cobranca.setMetodo(request.metodo() != null ? request.metodo() : CobrancaMetodoEnum.PIX);
                cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
                cobranca.setDataCriacao(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
                
                // Aplicar strategy específica do método
                CobrancaCriacaoStrategy strategy = strategyRegistry.getStrategy(cobranca.getMetodo());
                cobranca = strategy.executar(cobranca);
                
                // Persistir
                cobranca = repository.save(cobranca);
                
                // Publicar evento
                eventPublisher.publicarCobrancaCriada(cobranca);
                
                // Retornar DTO básico
                return new CobrancaBasicoResponseDTO(
                    cobranca.getId(),
                    cobranca.getTxid(),
                    cobranca.getCopiaECola(),
                    cobranca.getDataExpiracao(), 
                    cobranca.getTransactionId()
                );
            });
            
        } catch (LockIndisponivelException ex) {
            throw ex;  // Propaga exceção de lock
        } catch (Exception ex) {
            throw new RuntimeException("Erro ao criar cobrança.", ex);
        }
    }
}
```

**Payload Request:**
```json
{
  "valor": 25.50,
  "tipo": "RECARGA",  
  "metodo": "PIX"
}
```

**Payload Response (sucesso):**
```json
{
  "id": 123,
  "txid": "PIX1640995200123",
  "copiaECola": "00020126580014BR.GOV.BCB.PIX0136123e4567-e12b-12d3-a456-426655440000520400005303986540525.505802BR5915Joao Silva     6008BRASILIA62070503***63042C5A",
  "dataExpiracao": "2024-12-15T12:00:00",
  "transactionId": null
}
```

**Payload Response (erro lock):**
```json
{
  "codigo": "LOCK_INDISPONIVEL",
  "mensagem": "Geração de cobrança em andamento."
}
```

**Cenários de Teste (obrigatórios):**
- ✅ #1: `criarCobranca` sucesso PIX - cria cobrança com txid, copiaECola e dataExpiracao preenchidos
- ✅ #2: `criarCobrança` com lock indisponível - lança LockIndisponivelException com mensagem correta
- ✅ #3: `criarCobrança` com exceção inesperada - mapeia para RuntimeException "Erro ao criar cobrança."
- ✅ Defaults aplicados corretamente (tipo=RECARGA, metodo=PIX quando null)
- ✅ UserContext extraído corretamente e nomeSolicitante montado
- ✅ Evento Kafka publicado na criação

**Critérios de Aceite:**
- [ ] Lock distribuído por usuário funcional (TTL 5s)
- [ ] Strategy aplicada conforme método de pagamento
- [ ] Defaults aplicados para tipo e método quando não informados
- [ ] Persistência e evento Kafka executados
- [ ] Tratamento de exceções conforme especificação

---
## Task 9: CobrancaService - Consulta de Cobrança (GET)

**Descrição:** Implementar consulta por ID com lógica de versionamento (retorna versão mais recente) e consulta de status externo para cobranças PIX pendentes. Criar nova versão quando status externo mudou.

**Arquivos:** `src/main/java/com/v/challenge/service/CobrancaService.java`

**Service Implementation:**
```java
public CobrancaCompletoResponseDTO consultarCobranca(Long id) {
    // Busca cobrança original
    Cobranca cobrancaOriginal = repository.findById(id)
        .orElseThrow(() -> new CobrancaNaoEncontradaException("Cobrança não encontrada"));
    
    // Busca versão mais recente (pode ser a própria se não tem filhas)
    Cobranca cobrancaAtual = repository.findVersaoMaisRecente(id)
        .orElse(cobrancaOriginal);
    
    // Se PIX e status permite consulta externa
    if (cobrancaAtual.getMetodo() == CobrancaMetodoEnum.PIX && 
        isStatusConsultavel(cobrancaAtual.getStatus())) {
        
        CobrancaStatusEnum statusExterno = statusConsultaExternaClient
            .consultarStatus(cobrancaAtual.getTxid());
            
        // Se status mudou, criar nova versão
        if (statusExterno != cobrancaAtual.getStatus()) {
            CobrancaStatusEnum statusAnterior = cobrancaAtual.getStatus();
            
            Cobranca novaVersao = criarNovaVersao(cobrancaAtual);
            novaVersao.setStatus(statusExterno);
            novaVersao.setDataCriacao(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
            
            novaVersao = repository.save(novaVersao);
            eventPublisher.publicarStatusAlterado(novaVersao, statusAnterior);
            
            cobrancaAtual = novaVersao;
        }
    }
    
    return mapearParaResponseCompleto(cobrancaAtual);
}

private boolean isStatusConsultavel(CobrancaStatusEnum status) {
    return Set.of(
        CobrancaStatusEnum.SOLICITADA,
        CobrancaStatusEnum.EXPIRADA, 
        CobrancaStatusEnum.ERRO_APROVACAO_PEDIDO,
        CobrancaStatusEnum.EM_REPROCESSAMENTO,
        CobrancaStatusEnum.ERRO_ANALISE_PENDENTE
    ).contains(status);
}

private Cobranca criarNovaVersao(Cobranca cobrancaOriginal) {
    Cobranca novaVersao = new Cobranca();
    // Copiar todos os campos exceto ID e dataCriacao
    BeanUtils.copyProperties(cobrancaOriginal, novaVersao, "id", "dataCriacao");
    
    // Se a cobrança original já é uma versão, aponta para a mesma origem
    // Senão, aponta para a própria cobrança como origem
    novaVersao.setIdCobrancaOrigem(
        cobrancaOriginal.getIdCobrancaOrigem() != null ? 
        cobrancaOriginal.getIdCobrancaOrigem() : 
        cobrancaOriginal.getId()
    );
    
    return novaVersao;
}
```

**Mock StatusConsultaExternaClient:**
```java
@Component
public class StatusConsultaExternaClient {
    
    public CobrancaStatusEnum consultarStatus(String txid) {
        // Simula consulta externa retornando status aleatório para demonstração
        return switch (txid.hashCode() % 3) {
            case 0 -> CobrancaStatusEnum.FINALIZADA;
            case 1 -> CobrancaStatusEnum.EXPIRADA;
            default -> CobrancaStatusEnum.SOLICITADA;
        };
    }
}
```

**Payload Response (sucesso):**
```json
{
  "id": 124,
  "txid": "PIX1640995200123",
  "idUsuario": "user-123",
  "tipo": "RECARGA",
  "metodo": "PIX", 
  "status": "FINALIZADA",
  "valorSolicitado": 25.50,
  "valorPago": 25.50,
  "dataCriacao": "2024-12-15T10:00:00",
  "dataExpiracao": "2024-12-15T12:00:00", 
  "dataFinalizada": "2024-12-15T10:30:00"
}
```

**Payload Response (erro):**
```json
{
  "codigo": "COBRANCA_NAO_ENCONTRADA",
  "mensagem": "Cobrança não encontrada"
}
```

**Cenários de Teste:**
- ✅ Consulta cobrança existente retorna versão mais recente
- ✅ Consulta PIX com status consultável verifica status externo
- ✅ Status externo diferente cria nova versão com idCobrancaOrigem
- ✅ Status externo igual não cria nova versão
- ✅ Cobrança não encontrada retorna 404

**Critérios de Aceite:**
- [ ] Sempre retorna versão mais recente da cadeia de versionamento
- [ ] Consulta status externo para PIX em status consultáveis
- [ ] Nova versão criada quando status externo mudou
- [ ] Evento publicado na mudança de status
- [ ] 404 para cobrança inexistente

---
## Task 10: CobrancaService - Webhook PIX (POST)

**Descrição:** Implementar processamento de notificações PIX que finalizam cobranças pendentes. Ignorar payload vazio, txid vazio, cobrança inexistente ou já finalizada. Criar nova versão com status FINALIZADA e timezone São Paulo.

**Arquivos:** `src/main/java/com/v/challenge/service/CobrancaService.java`

**Service Implementation:**
```java
public void processarWebhookPix(PixWebhookDTO webhook) {
    if (webhook == null || webhook.pix() == null || webhook.pix().isEmpty()) {
        return; // Ignora payload vazio
    }
    
    for (PixWebhookItemDTO item : webhook.pix()) {
        processarItemWebhook(item);
    }
}

private void processarItemWebhook(PixWebhookItemDTO item) {
    if (item.txid() == null || item.txid().trim().isEmpty()) {
        return; // Ignora txid vazio
    }
    
    // Busca cobrança mais recente por txid
    Optional<Cobranca> cobrancaOpt = repository.findTopByTxidOrderByDataCriacaoDesc(item.txid());
    if (cobrancaOpt.isEmpty()) {
        return; // Ignora se não encontrou cobrança
    }
    
    Cobranca cobrancaAtual = cobrancaOpt.get();
    if (cobrancaAtual.getStatus() == CobrancaStatusEnum.FINALIZADA) {
        return; // Ignora se já finalizada
    }
    
    // Criar nova versão finalizada
    CobrancaStatusEnum statusAnterior = cobrancaAtual.getStatus();
    
    Cobranca novaVersao = criarNovaVersao(cobrancaAtual);
    novaVersao.setStatus(CobrancaStatusEnum.FINALIZADA);
    novaVersao.setValorPago(item.valor());
    novaVersao.setDataFinalizada(
        LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))
    );
    novaVersao.setDataCriacao(
        LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))
    );
    
    repository.save(novaVersao);
    eventPublisher.publicarStatusAlterado(novaVersao, statusAnterior);
}
```

**Payload Request:**
```json
{
  "pix": [
    {
      "txid": "PIX1640995200123",
      "horario": "2024-12-15T13:02:30Z",
      "valor": 25.50
    },
    {
      "txid": "PIX1640995200456", 
      "horario": "2024-12-15T13:05:15Z",
      "valor": 50.00
    }
  ]
}
```

**Payload Response:**
```
HTTP 200 OK
(corpo vazio)
```

**Cenários de Teste (obrigatórios):**
- ✅ #4: `processarNotificacaoWebhookPix` finalizando cobrança pendente - cria nova versão com status FINALIZADA
- ✅ #5: `processarNotificacaoWebhookPix` ignorando cobrança já finalizada - não cria nova versão
- ✅ Payload null/vazio é ignorado sem erro
- ✅ Item com txid vazio é ignorado
- ✅ Cobrança não encontrada por txid é ignorada
- ✅ Timezone America/Sao_Paulo aplicado na dataFinalizada

**Critérios de Aceite:**
- [ ] Processa lista de notificações PIX corretamente
- [ ] Ignora casos especificados (payload vazio, txid vazio, etc.)
- [ ] Cria nova versão apenas para cobranças não finalizadas
- [ ] Aplica timezone São Paulo nas datas
- [ ] Retorna 200 OK sempre

---
## Task 11: CobrancaService - Validar Checkout Cartão (POST)

**Descrição:** Implementar validação 3DS chamando client externo com dados de autenticação (cavv, xid, eci) e atualizando cobrança com resultado da autorização.

**Arquivos:** `src/main/java/com/v/challenge/service/CobrancaService.java`

**Service Implementation:**
```java
public void validarCheckout(String transactionId, CheckoutValidateRequestDTO request) {
    Cobranca cobranca = repository.findByTransactionId(transactionId)
        .orElseThrow(() -> new CobrancaNaoEncontradaException("Cobrança não encontrada"));
    
    // Chama client externo para validação 3DS
    CheckoutValidationResponse response = checkoutValidationClient.validarCheckout(
        transactionId,
        request.cavv(),
        request.xid(), 
        request.eci()
    );
    
    // Atualizar cobrança com dados de autorização
    CobrancaStatusEnum statusAnterior = cobranca.getStatus();
    
    if (response.aprovado()) {
        cobranca.setStatus(CobrancaStatusEnum.FINALIZADA);
        cobranca.setValorPago(cobranca.getValorSolicitacao());
        cobranca.setDataFinalizada(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
    } else {
        cobranca.setStatus(CobrancaStatusEnum.ERRO_APROVACAO_PEDIDO);
    }
    
    // Adicionar dados de autorização (opcional - depende do response do client)
    cobranca.setThreeDsPayload(response.threeDsResult());
    
    repository.save(cobranca);
    eventPublisher.publicarStatusAlterado(cobranca, statusAnterior);
}
```

**Mock CheckoutValidationClient:**
```java
@Component
public class CheckoutValidationClient {
    
    public CheckoutValidationResponse validarCheckout(String transactionId, 
                                                     String cavv, 
                                                     String xid, 
                                                     String eci) {
        // Simula validação 3DS - aprova se eci for "05"
        boolean aprovado = "05".equals(eci);
        
        return new CheckoutValidationResponse(
            aprovado,
            aprovado ? "APPROVED" : "DECLINED",
            String.format("3DS validation result for txn %s", transactionId)
        );
    }
}

public record CheckoutValidationResponse(
    boolean aprovado,
    String resultado,
    String threeDsResult
) {}
```

**Payload Request:**
```json
{
  "cavv": "AAABBBCCCDDDEEEFFFGGGHHHIII=",
  "xid": "XYZ123456789", 
  "eci": "05"
}
```

**Payload Response:**
```
HTTP 200 OK
(corpo vazio)
```

**Cenários de Teste (obrigatórios):**
- ✅ #6: `validarCheckout` atualizando cobrança existente - chama client e atualiza status conforme aprovação
- ✅ TransactionId não encontrado retorna 404
- ✅ Validação aprovada (eci=05) finaliza cobrança
- ✅ Validação recusada marca erro de aprovação
- ✅ Dados 3DS salvos na cobrança

**Critérios de Aceite:**
- [ ] Busca cobrança por transactionId
- [ ] Chama client de validação 3DS
- [ ] Atualiza status conforme resultado da validação
- [ ] Persiste dados de autorização
- [ ] Publica evento de mudança de status

---
## Task 12: Controller - Wiring dos Endpoints

**Descrição:** Criar controller REST que conecta DTOs ao service, implementando todos os endpoints obrigatórios com validação de entrada e mapeamento de respostas correto.

**Arquivos:** `src/main/java/com/v/challenge/controller/CobrancaController.java`

**Controller Implementation:**
```java
@RestController
@RequestMapping("/api/v1/cobrancas")
@Validated
public class CobrancaController {
    
    private final CobrancaService cobrancaService;
    
    @PostMapping
    public ResponseEntity<CobrancaBasicoResponseDTO> criarCobranca(
            @Valid @RequestBody CobrancaRequestDTO request) {
        
        CobrancaBasicoResponseDTO response = cobrancaService.criarCobranca(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<CobrancaCompletoResponseDTO> consultarCobranca(
            @PathVariable Long id) {
        
        CobrancaCompletoResponseDTO response = cobrancaService.consultarCobranca(id);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/webhook/pix")  
    public ResponseEntity<Void> processarWebhookPix(
            @RequestBody PixWebhookDTO webhook) {
        
        cobrancaService.processarWebhookPix(webhook);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{transactionId}/validate")
    public ResponseEntity<Void> validarCheckout(
            @PathVariable String transactionId,
            @Valid @RequestBody CheckoutValidateRequestDTO request) {
        
        cobrancaService.validarCheckout(transactionId, request);
        return ResponseEntity.ok().build();
    }
}
```

**Exemplos de Request/Response:**

**POST /api/v1/cobrancas:**
```bash
curl -X POST http://localhost:8080/api/v1/cobrancas \
  -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "valor": 25.50,
    "tipo": "RECARGA",
    "metodo": "PIX"
  }'
```

**GET /api/v1/cobrancas/123:**
```bash
curl -X GET http://localhost:8080/api/v1/cobrancas/123 \
  -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
```

**POST /api/v1/cobrancas/webhook/pix:**
```bash
curl -X POST http://localhost:8080/api/v1/cobrancas/webhook/pix \
  -H "Content-Type: application/json" \
  -d '{
    "pix": [
      {
        "txid": "PIX1640995200123",
        "horario": "2024-12-15T13:02:30Z", 
        "valor": 25.50
      }
    ]
  }'
```

**POST /api/v1/cobrancas/TXN123/validate:**
```bash
curl -X POST http://localhost:8080/api/v1/cobrancas/TXN123/validate \
  -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "cavv": "AAABBB",
    "xid": "XYZ123",
    "eci": "05"
  }'
```

**Cenários de Teste:**
- ✅ POST retorna 201 Created com payload correto
- ✅ GET retorna 200 OK com payload detalhado
- ✅ Webhook PIX retorna 200 OK (corpo vazio)
- ✅ Validate retorna 200 OK (corpo vazio)
- ✅ Bean Validation rejeita requests inválidos com 400
- ✅ Requests sem JWT retornam 401 Unauthorized

**Critérios de Aceite:**
- [ ] Todos endpoints implementados conforme contrato
- [ ] Status codes HTTP corretos
- [ ] Bean Validation aplicada nos DTOs de request
- [ ] Autenticação JWT obrigatória (exceto webhook PIX)
- [ ] Headers Content-Type respeitados

---
## Task 13: Testes de Integração (Diferencial)

**Descrição:** Implementar testes de integração end-to-end com Testcontainers para PostgreSQL, Redis e Kafka. Testar fluxos completos e verificar eventos publicados.

**Arquivos:** `src/test/java/com/v/challenge/integration/CobrancaIntegrationTest.java`

**Test Setup:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
    "spring.profiles.active=integration-test"
})
class CobrancaIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("cobranças_test")
            .withUsername("test") 
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    
    @Container 
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private CobrancaRepository repository;
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);  // Mock para não depender do Kafka nos testes
        }
    }
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

**Teste: Fluxo POST → GET:**
```java
@Test
void devePermitirCriarEConsultarCobranca() {
    // Given
    String jwt = gerarJwtTest("user-123", "João", "Silva", "12345678901");
    CobrancaRequestDTO request = new CobrancaRequestDTO(
        new BigDecimal("25.50"), 
        CobrancaTipoEnum.RECARGA, 
        CobrancaMetodoEnum.PIX
    );
    
    // When - Criar cobrança
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(jwt);
    
    ResponseEntity<CobrancaBasicoResponseDTO> createResponse = restTemplate.exchange(
        "/api/v1/cobrancas",
        HttpMethod.POST,
        new HttpEntity<>(request, headers),
        CobrancaBasicoResponseDTO.class
    );
    
    // Then - Verificar criação
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(createResponse.getBody().id()).isNotNull();
    assertThat(createResponse.getBody().txid()).startsWith("PIX");
    
    // When - Consultar cobrança
    ResponseEntity<CobrancaCompletoResponseDTO> getResponse = restTemplate.exchange(
        "/api/v1/cobrancas/" + createResponse.getBody().id(),
        HttpMethod.GET, 
        new HttpEntity<>(headers),
        CobrancaCompletoResponseDTO.class
    );
    
    // Then - Verificar consulta
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().status()).isEqualTo(CobrancaStatusEnum.SOLICITADA);
    assertThat(getResponse.getBody().idUsuario()).isEqualTo("user-123");
}
```

**Teste: Fluxo Webhook PIX:**
```java
@Test
void deveProcessarWebhookPixEFinalizarCobranca() {
    // Given - Criar cobrança PIX primeiro
    Cobranca cobranca = new Cobranca();
    cobranca.setIdUsuario("user-456");
    cobranca.setNomeSolicitante("Maria Santos");
    cobranca.setValorSolicitacao(new BigDecimal("50.00"));
    cobranca.setTipo(CobrancaTipoEnum.RECARGA);
    cobranca.setMetodo(CobrancaMetodoEnum.PIX);
    cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
    cobranca.setTxid("PIX123TEST");
    cobranca.setDataCriacao(LocalDateTime.now());
    
    repository.save(cobranca);
    
    // When - Processar webhook
    PixWebhookDTO webhook = new PixWebhookDTO(List.of(
        new PixWebhookItemDTO(
            "PIX123TEST",
            LocalDateTime.now(),
            new BigDecimal("50.00")
        )
    ));
    
    ResponseEntity<Void> webhookResponse = restTemplate.exchange(
        "/api/v1/cobrancas/webhook/pix",
        HttpMethod.POST,
        new HttpEntity<>(webhook),
        Void.class
    );
    
    // Then - Verificar finalização
    assertThat(webhookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    
    // Verificar que nova versão foi criada com status FINALIZADA
    List<Cobranca> cobranças = repository.findAll();
    Cobranca cobrancaFinalizada = cobranças.stream()
        .filter(c -> c.getStatus() == CobrancaStatusEnum.FINALIZADA)
        .findFirst()
        .orElseThrow();
        
    assertThat(cobrancaFinalizada.getValorPago()).isEqualTo(new BigDecimal("50.00"));
    assertThat(cobrancaFinalizada.getDataFinalizada()).isNotNull();
    assertThat(cobrancaFinalizada.getIdCobrancaOrigem()).isEqualTo(cobranca.getId());
}
```

**Cenários de Teste:**
- ✅ Fluxo POST /cobrancas → GET /cobrancas/{id} funcional
- ✅ Webhook PIX muda status para FINALIZADA com nova versão
- ✅ Lock distribuído impede criação simultânea (teste com threads)
- ✅ JWT inválido retorna 401 em endpoints protegidos
- ✅ Validação 3DS atualiza cobrança corretamente

**Critérios de Aceite:**
- [ ] Testcontainers configurados para PostgreSQL, Redis
- [ ] Fluxos end-to-end funcionais
- [ ] Persistência e versionamento testados
- [ ] Comportamento de lock distribuído verificado
- [ ] Testes executam via `mvn verify`

---
## Task 14: Cobertura, README e Finalização

**Descrição:** Configurar relatório de cobertura JaCoCo, escrever README completo e validar checklist do teste. Documentar premissas, trade-offs e exemplos de uso.

**Arquivos:** `pom.xml` (plugin JaCoCo), `README.md`

**JaCoCo Configuration:**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.8</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <includes>
                            <include>com.v.challenge.service.*</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**README.md Structure:**
```markdown
# Microserviço de Cobranças de Bilhetagem

## Sobre
Microserviço para gerenciamento de cobranças de bilhetagem eletrônica com suporte a PIX e cartão de crédito, 
lock distribuído para concorrência e versionamento de cobranças para auditoria completa.

## Tecnologias
- Java 17+, Spring Boot 3, Maven
- PostgreSQL (produção), H2 (testes)
- Redis (lock distribuído)
- Apache Kafka (eventos)
- Docker Compose

## Como Executar

### Pré-requisitos
- Docker e Docker Compose
- Java 17+
- Maven 3.8+

### Executar Localmente
```bash
# 1. Subir infraestrutura
docker-compose up -d postgres redis kafka

# 2. Executar aplicação
mvn spring-boot:run

# 3. Testar endpoints
curl -X POST http://localhost:8080/api/v1/cobrancas \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"valor": 25.50, "tipo": "RECARGA", "metodo": "PIX"}'
```

### Executar com Docker (Completo)
```bash
docker-compose up --build
```

## Endpoints

### POST /api/v1/cobrancas
Cria nova cobrança com lock distribuído por usuário.

### GET /api/v1/cobrancas/{id}  
Consulta cobrança retornando sempre a versão mais recente.

### POST /api/v1/cobrancas/webhook/pix
Processa notificações PIX (sem autenticação).

### POST /api/v1/cobrancas/{transactionId}/validate
Valida checkout 3DS para cartão de crédito.

## Testes
```bash
# Testes unitários + cobertura
mvn test

# Testes de integração
mvn verify

# Relatório de cobertura
open target/site/jacoco/index.html
```

## Premissas Adotadas
- JWT mockado para simplificar autenticação
- Clients externos são mocks/fakes
- Versionamento na mesma tabela (campo idCobrancaOrigem)
- Timezone oficial: America/Sao_Paulo

## Trade-offs
- **Versionamento**: Tabela única vs histórico separado
  - ✅ Simplicidade de implementação
  - ❌ Performance degradada a longo prazo
  - 💡 Evolução: particionar por data ou separar histórico

- **Lock Distribuído**: Redis vs Banco
  - ✅ Performance superior (~1ms vs ~10ms)
  - ✅ TTL automático como safety net
  - ❌ Dependência adicional (Redis)

## Arquitetura
- **Padrão Strategy**: criação PIX vs Cartão
- **Event-Driven**: Kafka para mudanças de status  
- **Lock Distribuído**: Redis SET NX EX
- **Versionamento**: Auditoria completa de mudanças
```

**Checklist Validação:**
```markdown
## Checklist Final do Teste

### Endpoints Obrigatórios
- [ ] POST /api/v1/cobrancas cria cobrança com lock por usuário
- [ ] GET /api/v1/cobrancas/{id} retorna payload detalhado  
- [ ] POST /api/v1/cobrancas/webhook/pix finaliza cobrança por txid
- [ ] POST /api/v1/cobrancas/{transactionId}/validate atualiza checkout

### Testes Unitários Obrigatórios  
- [ ] #1: criarCobranca sucesso PIX
- [ ] #2: criarCobranca com lock indisponível
- [ ] #3: criarCobranca com exceção inesperada → erro de negócio
- [ ] #4: processarNotificacaoWebhookPix finalizando cobrança pendente
- [ ] #5: processarNotificacaoWebhookPix ignorando cobrança já finalizada  
- [ ] #6: validarCheckout atualizando cobrança existente
- [ ] #7: LockExecutor garante unlock no finally mesmo com exceção

### Qualidade
- [ ] Cobertura ≥ 70% no pacote service atingida
- [ ] Build Maven completo passa (`mvn clean verify`)
- [ ] Docker Compose funcional (`docker-compose up`)
- [ ] README completo com exemplos de uso
```

**Cenários de Teste:**
- ✅ JaCoCo configurado e cobertura ≥ 70% no service
- ✅ README completo com execução, premissas e trade-offs
- ✅ `docker-compose up` levanta aplicação funcional
- ✅ Todos 7 testes unitários obrigatórios implementados
- ✅ Build completo passa sem erros

**Critérios de Aceite:**
- [ ] JaCoCo configurado e cobertura ≥ 70% no service
- [ ] README claro e completo
- [ ] `docker-compose up` funciona
- [ ] Todos itens do checklist validados
- [ ] Entrega pronta para avaliação técnica

---

## Conclusão

Este refinamento técnico detalha a implementação completa do microserviço de cobranças de bilhetagem conforme especificação do teste técnico. Cada task foi elaborada com:

- **Implementação específica** com exemplos de código
- **Cenários de teste** incluindo os 7 obrigatórios
- **Critérios de aceite** objetivos e mensuráveis
- **Payloads de exemplo** para demonstração
- **Trade-offs documentados** para decisões arquiteturais

O plano segue as melhores práticas de engenharia de software, priorizando:
1. **Corretude** das regras de negócio
2. **Qualidade** de modelagem e separação de responsabilidades  
3. **Robustez** em concorrência (lock distribuído)
4. **Testabilidade** com cobertura adequada
5. **Documentação** clara para execução e manutenção

Total de **14 tasks** sequenciais que levam à entrega completa do desafio técnico em **48 horas**.