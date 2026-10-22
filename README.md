# Mini Wallet Transaction System

Spring Boot 3 / Java 17 / Spring Data JPA (Hibernate) / Maven / H2 (default) or MySQL. REST API only, no frontend.

## Run

```bash
mvn spring-boot:run                                   # in-memory H2, nothing to install
mvn spring-boot:run -Dspring-boot.run.profiles=mysql  # MySQL (create DB `walletdb`; override DB_URL/DB_USERNAME/DB_PASSWORD)
mvn test                                              # unit + integration tests
```
H2 console (default profile): http://localhost:8080/h2-console , JDBC URL `jdbc:h2:mem:walletdb`, user `sa`, empty password.
Import `postman/MiniWallet.postman_collection.json` into Postman (run folder 1, then folder 2).

## API

| # | Method & path | Success | Errors |
|---|---|---|---|
| 1 | `POST /api/wallets` | 201 `{walletId,name,email,balance}` | 400 `VALIDATION_ERROR`, 409 `DUPLICATE_EMAIL` |
| 2 | `POST /api/wallets/{id}/deposit` | 200 `{walletId,previousBalance,amount,currentBalance}` | 400 `INVALID_DEPOSIT_AMOUNT`, 404 `WALLET_NOT_FOUND` |
| 3 | `POST /api/wallets/transfer` | 200 `{transactionId,fromWalletId,toWalletId,amount,status}` | 400 `INVALID_TRANSFER_AMOUNT` / `SAME_WALLET_TRANSFER` / `INSUFFICIENT_BALANCE`, 404 `WALLET_NOT_FOUND` |
| 4 | `GET /api/wallets/{id}` | 200 `{walletId,name,email,balance}` | 404 `WALLET_NOT_FOUND` |
| 5 | `GET /api/wallets/{id}/transactions` | 200 `[{transactionId,type,amount,status,createdAt}]` newest first, `[]` if none | 404 `WALLET_NOT_FOUND` |

Other errors: 400 `MALFORMED_REQUEST` / `INVALID_PARAMETER` / `VALIDATION_ERROR`, 409 `CONCURRENT_UPDATE` (lock timeout, retry), 500 `DATABASE_ERROR` / `INTERNAL_SERVER_ERROR`.
All errors look like `{"errorCode": "...", "message": "..."}` (`VALIDATION_ERROR` adds a `details` array). Stack traces, SQL and class names are logged server-side only.

## Layout

```
controller/  WalletController              thin REST layer, @Valid, no business logic
service/     WalletService, WalletServiceImpl   business rules + @Transactional boundaries
repository/  WalletRepository, WalletTransactionRepository   Spring Data JPA
entity/      Wallet, WalletTransaction, TransactionType, TransactionStatus
dto/         request/response records (entities are never returned from the API)
exception/   business exceptions + GlobalExceptionHandler (@RestControllerAdvice)
resources/   application*.properties, schema.sql (H2), schema-mysql.sql (MySQL)
```

## Database design

Exactly the suggested schema, with constraints (see `schema.sql`):

* `WALLET(id PK, name, email, balance DECIMAL(19,2), created_at)`: `UNIQUE(email)` and `CHECK(balance >= 0)` are enforced **in the database**.
* `WALLET_TRANSACTION(id PK, wallet_id FK -> wallet.id, type, amount DECIMAL(19,2), status, reference_id, created_at)`: FK, `CHECK(amount > 0)`, `CHECK` on type/status, indexes on `(wallet_id, created_at)` and `reference_id`.
* The schema is owned by SQL scripts (`ddl-auto=none`) so constraints are explicit and reviewable.
* Emails are trimmed and lower-cased before saving, so `Rahul@X.com` and `rahul@x.com` are the same wallet.
* `initialBalance` is optional and defaults to `0.00`. It is not recorded as a history row, because the allowed history types are DEPOSIT / TRANSFER_IN / TRANSFER_OUT only.

### Transaction-history design (documented choice)

A transfer writes **two rows** sharing one `reference_id` (UUID): `TRANSFER_OUT` on the sender and `TRANSFER_IN` on the receiver, with the same amount and timestamp. Each wallet's history is a simple query on `wallet_id`; both rows are written in the same DB transaction, so they are always present together. `transactionId` in the transfer response is the id of the sender's `TRANSFER_OUT` row. History is ordered `created_at DESC, id DESC` (id breaks ties within the same instant). A deposit row also gets its own `reference_id`.

## Transfer implementation and transaction strategy

`WalletServiceImpl.transfer` is annotated `@Transactional(rollbackFor = Exception.class)`:

1. Validate amount > 0 and `from != to` (no DB access yet).
2. Lock both wallets with `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) **in ascending id order**. Two concurrent transfers on the same wallet are serialised, so the balance check and debit cannot race (no overdraft / lost update), and A->B vs B->A cannot deadlock.
3. Check sufficient balance, then `debit` sender and `credit` receiver (`BigDecimal`), flush.
4. Insert the `TRANSFER_OUT` and `TRANSFER_IN` rows with the same `reference_id`.
5. Commit. Money is conserved: total before == total after.

**What if an exception happens after the sender was debited but before the receiver is credited?**
The exception propagates out of the `@Transactional` proxy, Spring marks the transaction rollback-only and calls `rollback()`. The sender's UPDATE is undone together with everything else in that transaction, so the DB never keeps a half transfer, and no history row is left behind. The client receives an error from `GlobalExceptionHandler`. This is verified by `WalletServiceTransactionTest.failureAfterDebitRollsBackEverything` (balances are flushed first, then a failure is injected, then balances are asserted unchanged).

Deposits follow the same rule: balance update + history row in one transaction.

### `@Transactional` behaviour and limitations (review talking points)

* It works through a **proxy**: only calls that come through the Spring bean are transactional. **Self-invocation** (`this.otherMethod()`) and calls on non-public methods bypass it.
* By default it rolls back only on **unchecked** exceptions (`RuntimeException`/`Error`). Checked exceptions commit unless `rollbackFor` is set (done here).
* Catching an exception inside the method and swallowing it prevents rollback. The one place we catch (`DataIntegrityViolationException` on create) always rethrows a business exception.
* It only covers **one database/transaction manager**. It cannot make external calls (email, payment gateway, message broker) atomic with the DB.
* Default isolation (READ COMMITTED / REPEATABLE READ depending on DB) does not by itself prevent lost updates, which is why explicit row locks are used for balance changes.
* Transactions are held on a DB connection, so keep them short; the `readOnly = true` reads are cheap hints.
* Spring's `Propagation.REQUIRES_NEW` would be needed to persist e.g. a `FAILED` audit row independent of the rolled-back transfer; the `FAILED` status exists in the enum for that future use.

## Business rules checklist

Wallet: name/email mandatory, valid + unique email (service check + DB unique constraint, race handled), initial balance >= 0, generated unique id, persisted.
Deposit: wallet exists, amount > 0, balance updated, history row written atomically.
Transfer: both wallets exist, different wallets, amount > 0, sufficient balance, exact debit/credit, history stored, no money created or lost, full rollback on failure, `BigDecimal` everywhere. Amounts with more than 2 decimals are rejected (`VALIDATION_ERROR`).
Get wallet / history: 404 for unknown wallet, no internal fields exposed, newest first, empty list when none, transfer visible in both histories with a shared reference id.
Constraints: no business logic in controllers, no double/float, no hardcoded ids/rules, entities never exposed.

## Notes for the Part 2 discussion

Idempotency keys on deposit/transfer (safe client retries), a ledger/double-entry model instead of a mutable balance, optimistic locking + retry vs. pessimistic locks at scale, outbox pattern for events/notifications, audit trail and FAILED attempts, pagination on history, currency support, authentication/authorisation (a caller may only move money out of their own wallet), rate limiting, and observability (metrics, correlation ids).
