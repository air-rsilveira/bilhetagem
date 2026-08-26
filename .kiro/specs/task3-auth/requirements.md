# Documento de Requisitos

## Introdução

Implementação do filtro de autenticação JWT para o microserviço de bilhetagem. O filtro extrai dados de um token JWT (implementação mock com chave simétrica HMAC-SHA256) e disponibiliza as informações do usuário via ThreadLocal para os services da aplicação. Requisições sem token válido são rejeitadas com HTTP 401, exceto endpoints públicos (actuator e webhook).

## Glossário

- **JwtAuthenticationFilter**: Filtro Spring que intercepta requisições HTTP, extrai e valida o token JWT do header Authorization
- **UserContext**: Record Java imutável que encapsula as informações do usuário autenticado (idUsuario, givenName, familyName, cpf)
- **UserContextHolder**: Classe utilitária que armazena o UserContext em ThreadLocal para acesso durante o ciclo de vida da requisição
- **SecurityConfig**: Configuração do Spring Security que define regras de autorização e registra o filtro JWT
- **Token_JWT**: Token no formato header.payload.signature codificado em Base64URL, assinado com HMAC-SHA256
- **Chave_Simétrica**: Chave fixa compartilhada usada para assinar e validar tokens JWT

## Requisitos

### Requisito 1

**User Story:** Como desenvolvedor do microserviço, quero que as informações do usuário autenticado estejam disponíveis via ThreadLocal, para que os services possam acessar dados do usuário sem parâmetros adicionais.

#### Critérios de Aceitação

1. THE UserContext SHALL encapsular os campos idUsuario, givenName, familyName e cpf como record imutável
2. THE UserContext SHALL fornecer um método getNomeCompleto que retorna a concatenação de givenName e familyName separados por espaço
3. THE UserContextHolder SHALL armazenar o UserContext em ThreadLocal isolado por thread
4. WHEN uma requisição é finalizada, THEN THE JwtAuthenticationFilter SHALL limpar o UserContext do ThreadLocal para evitar vazamento entre requisições

### Requisito 2

**User Story:** Como microserviço, quero validar tokens JWT em cada requisição, para que apenas usuários autenticados acessem endpoints protegidos.

#### Critérios de Aceitação

1. WHEN uma requisição contém um header Authorization com prefixo "Bearer " seguido de um token válido, THEN THE JwtAuthenticationFilter SHALL extrair as claims sub, given_name, family_name e cpf do payload do token
2. WHEN uma requisição contém um token JWT válido, THEN THE JwtAuthenticationFilter SHALL popular o UserContextHolder com um UserContext construído a partir das claims extraídas
3. WHEN uma requisição contém um token JWT válido, THEN THE JwtAuthenticationFilter SHALL popular o SecurityContext do Spring Security com uma Authentication contendo o idUsuario como principal
4. WHEN uma requisição não contém header Authorization, THEN THE JwtAuthenticationFilter SHALL retornar HTTP 401 Unauthorized sem invocar o restante da filter chain
5. WHEN uma requisição contém um token com assinatura inválida, THEN THE JwtAuthenticationFilter SHALL retornar HTTP 401 Unauthorized
6. WHEN uma requisição contém um token com payload malformado, THEN THE JwtAuthenticationFilter SHALL retornar HTTP 401 Unauthorized
7. WHEN uma requisição contém um token expirado (claim exp no passado), THEN THE JwtAuthenticationFilter SHALL retornar HTTP 401 Unauthorized

### Requisito 3

**User Story:** Como microserviço, quero que o Spring Security esteja configurado com o filtro JWT customizado, para que a autenticação seja aplicada automaticamente a todas as requisições protegidas.

#### Critérios de Aceitação

1. THE SecurityConfig SHALL registrar o JwtAuthenticationFilter antes do UsernamePasswordAuthenticationFilter na cadeia de filtros
2. THE SecurityConfig SHALL permitir acesso sem autenticação a endpoints que correspondem ao padrão /actuator/**
3. THE SecurityConfig SHALL permitir acesso sem autenticação a endpoints que correspondem ao padrão /api/v1/cobrancas/webhook/**
4. THE SecurityConfig SHALL exigir autenticação para todas as demais requisições
5. THE SecurityConfig SHALL desabilitar proteção CSRF
6. THE SecurityConfig SHALL configurar gerenciamento de sessão como STATELESS

### Requisito 4

**User Story:** Como desenvolvedor, quero um utilitário de teste para gerar tokens JWT válidos, para que os testes de integração possam simular requisições autenticadas facilmente.

#### Critérios de Aceitação

1. THE JwtTokenUtil SHALL gerar tokens JWT válidos assinados com a mesma Chave_Simétrica utilizada pelo JwtAuthenticationFilter
2. THE JwtTokenUtil SHALL permitir configurar claims individuais (sub, given_name, family_name, cpf) na geração do token
3. THE JwtTokenUtil SHALL permitir configurar o tempo de expiração do token gerado
4. WHEN JwtTokenUtil gera um token, THEN the token SHALL seguir o formato padrão JWT com três partes separadas por ponto (header.payload.signature)
