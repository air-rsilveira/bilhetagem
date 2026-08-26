# Product Summary

Bilhetagem is a billing/charging microservice for a ticketing system. It handles the creation, tracking, and lifecycle management of payment charges (cobranças) via PIX and credit card methods.

## Core Capabilities

- Create charges (PIX or credit card) with distributed locking per user
- Track charge lifecycle through multiple statuses (SOLICITADA → FINALIZADA/EXPIRADA/etc.)
- Receive PIX payment webhooks
- Validate 3DS authentication for credit card payments
- Query charge status with automatic external status reconciliation
- Version charges for full audit trail (new row per status change, linked via `idCobrancaOrigem`)
- Publish Kafka events on every status transition

## Domain Concepts

- **Cobrança**: A charge/billing request initiated by a user
- **Tipo**: RECARGA, RECARGA_TERCEIROS, ENVIO_CARTAO
- **Método**: PIX, CARTAO_CREDITO
- **Status lifecycle**: SOLICITADA(2) → AGUARDANDO_PAGAMENTO(3) → EM_PROCESSAMENTO(4) → FINALIZADA(5) / EXPIRADA(6) / CANCELADA(7) / ERRO_APROVACAO_PEDIDO(8) / EM_REPROCESSAMENTO(9) / ERRO_ANALISE_PENDENTE(10)
- **Versionamento**: Each status change creates a new `Cobranca` row pointing to the original via `idCobrancaOrigem`

## Language

The codebase, domain names, enums, error messages, and documentation are in Brazilian Portuguese. Maintain this convention.
