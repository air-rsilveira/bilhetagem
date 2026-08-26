# Plano de Implementação: Autenticação JWT Filter e UserContext

## Visão Geral

Implementação do filtro de autenticação JWT com chave simétrica HMAC-SHA256 para o microserviço de bilhetagem. O plano segue uma abordagem incremental: primeiro os componentes de dados (UserContext), depois a lógica de validação (Filter), depois a configuração (SecurityConfig), e por fim o utilitário de testes.

## Tasks

- [x] 1. Criar UserContext e UserContextHolder
  - [x] 1.1 Criar record UserContext e classe UserContextHolder
    - Criar `src/main/java/com/v/challenge/security/UserContext.java` como record com campos idUsuario, givenName, familyName, cpf e método getNomeCompleto()
    - Criar `src/main/java/com/v/challenge/security/UserContextHolder.java` com ThreadLocal<UserContext> e métodos setContext(), getContext(), clear()
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 1.2 Escrever testes unitários para UserContext e UserContextHolder
    - Testar que getNomeCompleto() concatena givenName + " " + familyName
    - Testar isolamento de ThreadLocal entre threads
    - Testar que clear() remove o contexto
    - **Property 5: Concatenação de Nome Completo**
    - **Validates: Requirements 1.2**
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. Implementar JwtAuthenticationFilter
  - [x] 2.1 Criar JwtAuthenticationFilter
    - Criar `src/main/java/com/v/challenge/security/JwtAuthenticationFilter.java` extends OncePerRequestFilter
    - Implementar shouldNotFilter() para excluir /actuator/** e /api/v1/cobrancas/webhook/**
    - Implementar extractToken() para extrair Bearer token do header Authorization
    - Implementar validateAndExtract() com validação HMAC-SHA256, parse de claims e verificação de expiração
    - Implementar computeHmac() com javax.crypto.Mac e chave simétrica fixa
    - Implementar extractJsonValue() para parse manual de JSON
    - Popular UserContextHolder e SecurityContext em caso de sucesso
    - Retornar 401 em caso de falha
    - Garantir limpeza do ThreadLocal no bloco finally
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 1.4_

  - [x] 2.2 Criar JwtTokenUtil (utilitário de teste)
    - Criar `src/test/java/com/v/challenge/security/JwtTokenUtil.java`
    - Implementar generateToken() com claims configuráveis e tempo de expiração
    - Implementar generateExpiredToken() para cenários de token expirado
    - Implementar generateTokenWithExp() para controle fino de expiração
    - Usar mesma chave simétrica do JwtAuthenticationFilter
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 2.3 Escrever property tests para JwtAuthenticationFilter
    - **Property 1: Round-trip de Claims JWT** — Para qualquer conjunto de claims válidas, gerar token e processar pelo filter deve preservar todas as claims
    - **Property 2: Rejeição de Assinatura Inválida** — Para qualquer token com assinatura alterada, filter deve rejeitar
    - **Property 3: Rejeição de Token Expirado** — Para qualquer token com exp no passado, filter deve rejeitar
    - **Property 4: Formato JWT Válido na Geração** — Tokens gerados devem ter 3 partes Base64URL
    - **Property 6: SecurityContext Populado com Principal Correto** — Principal do SecurityContext deve ser igual à claim sub
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.5, 2.7, 4.4**

- [x] 3. Checkpoint - Verificar componentes de autenticação
  - Garantir que todos os testes passam, perguntar ao usuário se houver dúvidas.

- [x] 4. Configurar Spring Security
  - [x] 4.1 Atualizar SecurityConfig
    - Modificar `src/main/java/com/v/challenge/security/SecurityConfig.java`
    - Injetar JwtAuthenticationFilter via construtor
    - Adicionar filtro antes de UsernamePasswordAuthenticationFilter
    - Configurar sessionManagement como STATELESS
    - Manter CSRF desabilitado
    - Permitir /actuator/** e /api/v1/cobrancas/webhook/** sem autenticação
    - Exigir autenticação para todas as demais requisições
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 4.2 Escrever testes de integração para SecurityConfig
    - Testar que /actuator/health é acessível sem token
    - Testar que /api/v1/cobrancas/webhook/** é acessível sem token
    - Testar que endpoint protegido sem token retorna 401
    - Testar que endpoint protegido com token válido retorna 200
    - Testar que endpoint protegido com token expirado retorna 401
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 5. Checkpoint final - Garantir integração completa
  - Garantir que todos os testes passam, perguntar ao usuário se houver dúvidas.

## Notes

- Tasks marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido
- Cada task referencia requisitos específicos para rastreabilidade
- Checkpoints garantem validação incremental
- Property tests validam propriedades universais de corretude
- A chave simétrica é fixa e compartilhada entre JwtAuthenticationFilter e JwtTokenUtil
- Linguagem de implementação: Java 17 com Spring Boot 3.2

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1"] },
    { "id": 2, "tasks": ["2.2"] },
    { "id": 3, "tasks": ["2.3", "4.1"] },
    { "id": 4, "tasks": ["4.2"] }
  ]
}
```
