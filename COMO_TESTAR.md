# Como testar a aplicação

Este documento descreve como subir a aplicação localmente e um fluxo sequencial de chamadas do Postman para validar o ciclo de vida de uma cobrança.

## 1. Como subir a aplicação

### 1.1. Docker Compose — subindo apenas a infraestrutura

Sobe apenas Postgres, Redis e Kafka (Zookeeper incluso). A aplicação **não** é iniciada por este comando, apenas as dependências de infra.

```bash
docker-compose -f docker/docker-compose.yml up -d postgres redis kafka
```

Verifique se os containers estão saudáveis:

```bash
docker-compose -f docker/docker-compose.yml ps
```

### 1.2. Maven — subindo a aplicação pelo terminal

Com a infra no ar, rode a aplicação no perfil `default` (porta 8080) usando o Maven Wrapper:

```bash
./mvnw spring-boot:run
```

Confirme que a aplicação subiu com o health check:

```bash
curl http://localhost:8080/actuator/health
```

## 2. Chamadas do Postman

Importe a collection `postman/bilhetagem.postman_collection.json`. Ela já vem com as variáveis `baseUrl` (`http://localhost:8080`) e um `jwt` de teste pré-gerado. As chamadas de criação salvam automaticamente `cobrancaId`, `txid` e `transactionId` nas variáveis da collection, então o fluxo abaixo deve ser executado **na ordem apresentada**.

Fluxo sequencial (criar → consultar → mudar de estado → consultar novamente):

### 2.1. Criando pagamento

```
- Criar cobrança (PIX)
- Cria uma cobrança PIX (valor 100.00, tipo RECARGA). Retorna 201 Created com os dados básicos e salva o `id` e o `txid` nas variáveis da collection para reuso nas próximas chamadas.
```

### 2.2. Consultando

```
- Consultar cobrança por id
- Consulta os dados completos da cobrança recém-criada usando o `{{cobrancaId}}` salvo. Neste ponto a cobrança ainda está pendente de pagamento; retorna 200 OK.
```

### 2.3. Mudando de estado

```
- Webhook PIX (público)
- Endpoint público que simula a confirmação do pagamento pelo provedor PIX, usando o `{{txid}}` salvo. Gera uma nova versão da cobrança com status FINALIZADA; retorna 200 OK.
```

### 2.4. Consultando novamente

```
- Consultar cobrança por id
- Consulta novamente a cobrança pelo `{{cobrancaId}}` para confirmar a mudança de estado. Agora o status refletido deve ser FINALIZADA; retorna 200 OK.
```

## 3. Template de instruções

Cada chamada do fluxo acima segue o template:

```
- <nome> da chamada da collection do postman
- Descrição do que o processo está fazendo
```
