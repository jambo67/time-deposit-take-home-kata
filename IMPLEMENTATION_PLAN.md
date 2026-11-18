# Java implementation plan and result

The user authorized implementation on 17 November 2026 and clarified that this should be proportionate to a roughly one-hour exercise.

## Implemented scope

- One Java 25 Maven module using Spring Boot 4.1.2.
- Protected `TimeDeposit` source unchanged; existing calculator API and behavior preserved.
- One `InterestPolicy` interface, a reusable annual-rate/day-window implementation, and a map of registrations.
- Independent `BalanceAdjustment` rules compose signed decimal credits and deductions from one immutable opening-balance context. Optional Spring beans are collected at startup; no adjustments are enabled by default.
- One application service, one repository interface, a JDBC adapter, and a REST controller.
- Exactly two challenge operations: GET `/time-deposits` and POST `/time-deposits/balance-updates`.
- PostgreSQL decimal storage, required withdrawals relationship, Flyway migration, and explicit demonstration seed data.
- One joined read; updates in batches of 500 using ordered row locks within one transaction.
- Pinned Maven Wrapper with distribution checksum, static OpenAPI contract, optional separate local Swagger UI, and startup/AI-workflow documentation.

To keep the solution small, GET returns a materialized list from one joined query. Streaming, additional modules, background jobs, and a generic rules engine were omitted.

## Compatibility and assumptions

The existing code is authoritative where the wording is ambiguous:

| Plan | Rate per invocation | Eligible days |
| --- | --- | --- |
| Basic | 1% / 12 | 31 onward |
| Student | 3% / 12 | 31 through 365 |
| Premium | 5% / 12 | 46 onward |

Preserve the original binary-double conversion and rounding of interest before addition. Days do not change. Each successful request applies another installment. Withdrawals are persisted/returned but do not affect interest without a supplied business rule. Money is stored at two-decimal scale.

Base-interest eligibility does not implicitly gate independent adjustments. All matching adjustments use the same opening balance and already-rounded base interest. Each rule owns its monetary rounding; the calculator sums returned decimal amounts before converting the combined balance to the shared model. Stateful once-only promotions and sequential compounding need explicit future business requirements.

Locking provides correct serial results for concurrent requests; it does not provide once-per-calendar-month deduplication. Batch processing bounds update memory; full-response size and the duration of the single update transaction remain scaling limits.

## Verification

- Before refactoring, 22 characterization tests passed against the original calculator.
- After the adjustment extension, 40 domain tests and 10 PostgreSQL/HTTP integration tests passed: 50 total, no failures or skips.
- Database tests cover withdrawal serialization, multi-batch updates, rollback after a later database or adjustment failure, Spring adjustment-bean discovery, combined balances, foreign keys, and two overlapping HTTP requests observed waiting on real database locks.
- The checked-in Maven Wrapper completed `clean verify` on JDK 25 and produced the executable Spring Boot jar. All 50 tests passed, including the local Swagger UI CORS assertion.

## Repository identity and delivery

Repository-local author and committer are `jambo67 <309137753+jambo67@users.noreply.github.com>`; identity and push guards remain enabled. Global Git settings are unchanged.

The user authorized committing the completed solution on 18 November 2026. Pushes, new branches, and upstream writes require a separate instruction.

See `java/README.md` for operation and extension instructions, and `AGENTS.md` for the repository working rules.
