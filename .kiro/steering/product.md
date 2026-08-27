# Product

**Bilhetagem** is a charge-management microservice ("Microserviço de Cobranças de Bilhetagem") for a ticketing/transit system. It handles the creation, tracking, and full lifecycle management of charges (`cobranças`).

## Core capabilities

- Create charges paid via **PIX** or **credit card** (`CARTAO_CREDITO`).
- Charge types: `RECARGA`, `RECARGA_TERCEIROS`, `ENVIO_CARTAO` (default `RECARGA`).
- Full audit trail through **versioning**: every status change creates a new row in the same `cobranca` table, linked to the original via `idCobrancaOrigem`.
- **Distributed lock per user** during charge creation (Redis `SET NX EX`, 5s TTL).
- **Event publishing** to Kafka on every status transition.
- PIX status is **reconciled automatically** with an external status query on read.
- Credit-card flow supports **3DS checkout validation** (`/validate`).
- PIX payments are confirmed via a **public webhook** (no auth).

## Key domain rules

- All timestamps use the `America/Sao_Paulo` timezone.
- A charge in `FINALIZADA` status is terminal and is not re-processed by the webhook.
- Auth is via **JWT Bearer** on all endpoints except the PIX webhook and Actuator.

## Important context

Several dependencies are **mocked for the challenge**: JWT uses a fixed hardcoded HMAC-SHA256 key, and the payment gateway, checkout (3DS) validation, and external status query are fake in-memory implementations with no real network calls. These are not production-ready.
