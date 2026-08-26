# Bilhetagem

Sistema de cobranças construído com Spring Boot.

## Limitações conhecidas

### Chave secreta do JWT hardcoded

Atualmente a chave secreta utilizada para assinar e validar os tokens JWT está definida diretamente no código-fonte (`JwtAuthenticationFilter.java`) como uma constante `SECRET_KEY`.

Isso é aceitável para desenvolvimento e testes, mas **não deve ir para produção** dessa forma. Os riscos incluem:

- Qualquer pessoa com acesso ao repositório pode ver a chave.
- Não é possível rotacionar a chave sem recompilar e fazer novo deploy.
- Todos os ambientes compartilham a mesma chave.

**Solução recomendada:** externalizar a chave para o `application.yml` com suporte a variável de ambiente (`JWT_SECRET_KEY`) e injetá-la via `@Value`.
