# Banking Wallet

A Spring Boot REST API for creating wallets and moving money between them. It is a **learning project** for JPA, Flyway, transactions, and concurrency — not a production payments system.

Wallets start at zero. You deposit, withdraw, and transfer. Transfers lock both wallets in a **stable ID order** so two overlapping transfers cannot deadlock. Each money movement also writes a `wallet_transactions` row.

---

## Tech stack

| Piece | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| HTTP | Spring Web MVC |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL (`banking_wallet_db`) |
| Schema | Flyway (`src/main/resources/db/migration`) |
| Validation | Jakarta Validation |
| Build | Maven Wrapper (`./mvnw`) |

---

## Architecture

```
Client
  → WalletController          /api/v1/wallets
      → WalletService         transactions + business rules
          → WalletRepository / WalletTransactionRepository
              → MySQL
```

Errors are mapped in `GlobalExceptionHandler` to a shared `ErrorResponse` body (`timestamp`, `status`, `error`, `message`, `path`).

```
com.dhruv.banking_wallet
├── BankingWalletApplication
├── controller/WalletController
├── service/WalletService
├── repository/WalletRepository
│              WalletTransactionRepository
├── entity/Wallet
│          WalletTransaction
│          WalletTransactionType   (DEPOSIT | WITHDRAWAL)
├── dto/   WalletRequest, WalletResponse, MoneyRequest,
│          TransferRequest, WalletSummaryResponse
└── exception/
    GlobalExceptionHandler
    WalletNotFoundException          → 404
    InsufficientBalanceException     → 400
    WalletConcurrentModificationException → 409
    ErrorResponse
```

---

## Domain model

### `wallets`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK | auto-increment |
| `owner_name` | `VARCHAR(100)` | required |
| `balance` | `DECIMAL(19,4)` | never stored as `double` |
| `currency` | `VARCHAR(3)` | ISO-style, e.g. `USD` |
| `created_at` / `updated_at` | `DATETIME` | set in the service |
| `version` | `BIGINT` | JPA `@Version` (optimistic lock); Flyway V4 |

JPA mapping: `Wallet` → `WalletTransaction` is `@OneToMany(LAZY)` with `@BatchSize(size = 10)`.

### `wallet_transactions`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK | auto-increment |
| `wallet_id` | FK → `wallets.id` | required |
| `type` | `VARCHAR(20)` | `DEPOSIT` or `WITHDRAWAL` |
| `amount` | `DECIMAL(19,4)` | always positive |
| `created_at` | `DATETIME` | |

Index (Flyway V3): `(wallet_id, created_at)` for history-style lookups.

A transfer writes **two** rows: `WITHDRAWAL` on the source wallet and `DEPOSIT` on the destination.

---

## Flyway migrations

Files live in `src/main/resources/db/migration/`.

| Version | File | What it does |
|---|---|---|
| V1 | `V1__create_wallets_table.sql` | create `wallets` |
| V2 | `V2__create_wallet_transactions_table.sql` | create `wallet_transactions` + FK |
| V3 | `V3__add_wallet_transaction_history_index.sql` | history index |
| V4 | `V4__add_wallet_version.sql` | add `wallets.version` (optimistic lock column) |

Hibernate DDL is `validate` only. Schema changes must go through Flyway. `Wallet.version` is a JPA `@Version` field, so a stale in-memory wallet fails with `409 Conflict`.

---

## Money-moving rules

### Create

- New wallet balance is always `0`.
- `ownerName` required, max 100 chars.
- `currency` must match `^[A-Z]{3}$`.

### Deposit / withdraw

- Amount must be `>= 0.01`.
- Withdraw fails with `400` if `balance < amount`.
- Both operations run in `@Transactional` and write a transaction row.

### Transfer (the important one)

1. Reject if source id equals destination id.
2. Load both wallets with `SELECT … FOR UPDATE` (`LockModeType.PESSIMISTIC_WRITE`).
3. Always lock **smaller id first**, then larger id — same order for every transfer, so concurrent A→B and B→A cannot deadlock.
4. Re-check balance **after** the locks are held.
5. Debit source, credit destination, write both transaction rows.
6. Return the **source** wallet as `WalletResponse`.

---

## HTTP API

Base path: `http://localhost:8080/api/v1/wallets`

Default Spring Boot port is **8080** (not overridden).

### Create wallet — `POST /api/v1/wallets`

Status: `201 Created`

```json
{
  "ownerName": "Ada Lovelace",
  "currency": "USD"
}
```

```bash
curl -s -X POST http://localhost:8080/api/v1/wallets \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Ada Lovelace","currency":"USD"}'
```

Response:

```json
{
  "id": 1,
  "ownerName": "Ada Lovelace",
  "balance": 0,
  "currency": "USD",
  "createdAt": "2026-09-17T18:00:00",
  "updatedAt": "2026-09-17T18:00:00"
}
```

### Get wallet — `GET /api/v1/wallets/{id}`

Status: `200`, or `404` if missing.

### Deposit — `POST /api/v1/wallets/{id}/deposit`

```json
{ "amount": 100.00 }
```

### Withdraw — `POST /api/v1/wallets/{id}/withdraw`

```json
{ "amount": 25.50 }
```

Fails with `400` when funds are not enough:

```json
{
  "timestamp": "2026-09-17T18:01:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient wallet balance. Current balance: 10.0000, withdrawal amount: 25.50",
  "path": "/api/v1/wallets/1/withdraw"
}
```

### Transfer — `POST /api/v1/wallets/{id}/transfer`

`{id}` is the **source** wallet.

```json
{
  "toWalletId": 2,
  "amount": 10.00
}
```

```bash
curl -s -X POST http://localhost:8080/api/v1/wallets/1/transfer \
  -H "Content-Type: application/json" \
  -d '{"toWalletId":2,"amount":10.00}'
```

---

## Error responses

All mapped errors use the same record:

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "Wallet was modified by another transaction. Please retry.",
  "path": "/api/v1/wallets/1/deposit"
}
```

| Exception | HTTP |
|---|---|
| `WalletNotFoundException` | `404 Not Found` |
| `InsufficientBalanceException` | `400 Bad Request` |
| `MethodArgumentNotValidException` | `400 Bad Request` (first field error) |
| `OptimisticLockException` / `ObjectOptimisticLockingFailureException` / `WalletConcurrentModificationException` | `409 Conflict` |

---

## JPA / isolation lab endpoints

These exist so you can watch SQL in the console (`spring.jpa.show-sql=true`). They are **not** product APIs. Several sleep for ~10 seconds so you can fire a second request in another terminal.

| Method | Path | What it demonstrates |
|---|---|---|
| `GET` | `/api/v1/wallets/{id}/test-lazy` | lazy `Wallet.transactions` inside an open transaction |
| `GET` | `/api/v1/wallets/transactions/{id}/test-eager` | lazy `WalletTransaction.wallet` |
| `GET` | `/api/v1/wallets/test-n-plus-one` | N+1: `findAll()` then `wallet.getTransactions().size()` |
| `GET` | `/api/v1/wallets/test-entity-graph` | same data via `@EntityGraph` |
| `GET` | `/api/v1/wallets/test-persistence-context` | first-level cache (`wallet1 == wallet2`) |
| `GET` | `/api/v1/wallets/test-dirty-checking` | dirty checking + `entityManager.flush()` |
| `GET` | `/api/v1/wallets/test-repeatable-read/{walletId}` | `READ_COMMITTED` + 10s sleep + `clear()` |
| `GET` | `/api/v1/wallets/test-uncommitted-update/{walletId}` | uncommitted update, then **intentional rollback** |
| `GET` | `/api/v1/wallets/test-dirty-read/{walletId}` | `READ_UNCOMMITTED` read |
| `GET` | `/api/v1/wallets/test-serializable/{walletId}` | `SERIALIZABLE` + 10s sleep |

`WalletRepository` also has unused-from-controller helpers you can call from a test or a new endpoint:

- `findAllWithTransactions()` — `JOIN FETCH`
- `findAllWithTransactionsUsingEntityGraph()`
- `findWalletSummaries()` — DTO projection with `COUNT(t)`
- `findByIdForUpdate(id)` — used by transfer

---

## Run locally

### Prerequisites

- JDK 21
- Maven (or just use `./mvnw`)
- MySQL 8 listening on `localhost:3306`

### Database

```sql
CREATE DATABASE banking_wallet_db;
```

Create a user (or use `root`) and put credentials in `src/main/resources/application.properties`. **Do not commit a real password.** Use a local-only value or environment variables:

```properties
spring.application.name=banking-wallet

spring.datasource.url=jdbc:mysql://localhost:3306/banking_wallet_db
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:changeme}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true

spring.flyway.enabled=true
```

### Start

```bash
cd banking-wallet
./mvnw spring-boot:run
```

Flyway runs V1–V4 on startup.

### Test

```bash
./mvnw test
```

There is currently a context-load test only (`BankingWalletApplicationTests`).

---

## What this project is / is not

**Is**

- A wallet ledger with deposits, withdrawals, and deadlock-safe transfers
- A sandbox for lazy loading, N+1, persistence context, dirty checking, and isolation levels

**Is not**

- Authenticated / multi-tenant banking
- Idempotent payment APIs (no client request id)
- Pessimistic locking on deposit/withdraw (those rely on `@Version`); transfer still uses `SELECT … FOR UPDATE`

---

