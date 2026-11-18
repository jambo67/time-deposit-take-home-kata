# Java time-deposit solution

A small Java 25 / Spring Boot application with two operations, PostgreSQL storage, and extensible interest policies.

## Run

Requirements: JDK 25, Docker, and a POSIX shell (or use `mvnw.cmd` on Windows). Ensure `JAVA_HOME` points to JDK 25. Maven 3.9.16 is downloaded by the standard wrapper and checked against its pinned SHA-256.

From this directory:

```sh
docker compose up -d --wait db
./mvnw spring-boot:run
```

After the application reports that it has started, optionally load the example data from another terminal in this directory:

```sh
docker compose exec -T db psql -U time_deposit -d time_deposit < dev/seed.sql
```

Run the seed script once on an empty database. Flyway creates the schema automatically before the application starts.

The database is exposed only on `127.0.0.1:15432`. The bundled username/password are demonstration values for this local container. Override `DB_URL`, `DB_USER`, and `DB_PASSWORD` when using another database.

## Call the two operations

```sh
curl http://localhost:8080/time-deposits
curl -i -X POST http://localhost:8080/time-deposits/balance-updates
```

GET returns a JSON array, including deposits with no withdrawals:

```json
[
  {
    "id": 1,
    "planType": "basic",
    "balance": 1200.00,
    "days": 31,
    "withdrawals": [
      {"id": 1, "timeDepositId": 1, "amount": 50.00, "date": "2026-01-15"}
    ]
  }
]
```

POST returns `204 No Content` after all balance changes commit.

The contract is [openapi.yaml](openapi.yaml). To try it through a local Swagger UI:

```sh
docker compose --profile docs up -d swagger
```

Open [http://localhost:8081](http://localhost:8081), expand either operation, choose **Try it out**, and **Execute**. The API allows that local documentation origin. Swagger UI runs separately, with external specification validation disabled; it adds no application API operations.

Stop the local demonstration services with `docker compose --profile docs stop`.

## Behavior and assumptions

The original calculator is the compatibility reference, as required by the challenge.

| Plan | Rate used by the original code | Eligible days |
| --- | --- | --- |
| Basic | 1% / 12 per invocation | 31 onward |
| Student | 3% / 12 per invocation | 31 through 365 |
| Premium | 5% / 12 per invocation | 46 onward |

- The brief says monthly interest; the supplied implementation divides the percentages by 12. These are therefore treated as annual rates when computing one monthly installment.
- Each successful POST applies another installment. There is no specified calendar-period identifier or once-per-month restriction. Locking prevents lost updates, not repeated credits from repeated successful calls.
- Days are unchanged.
- Withdrawals are stored and returned. No supplied rule changes interest or deducts withdrawal amounts during this operation.
- Unknown non-null plan types retain their current zero-interest behavior.
- Interest retains the original `new BigDecimal(double)` conversion and `HALF_UP` rounding to two decimals before adding it to the balance. In particular, the binary-double half-cent behavior is deliberately preserved.
- Stored balances and withdrawal amounts use `NUMERIC(19,2)`. Conversion to the protected double-based calculator is confined to the persistence boundary.
- Preserving that calculator also preserves its floating-point precision limits for very large balances; decimal database storage does not make its arithmetic arbitrary-precision.
- Deposits are returned by ID and withdrawals by date, then ID. Missing withdrawal collections are empty arrays.
- The update is all-or-nothing, including failures in a later batch. Invalid-input handling and custom exception responses are outside the exercise.

## Design and extension

```text
REST controller -> application service -> calculator
                         |
                  repository interface
                         ^
                     JDBC adapter

calculator -> base interest policy
           -> all matching balance adjustments (one opening-balance snapshot)
```

The shared `TimeDeposit` class is unchanged. The calculator keeps its no-argument constructor and `updateBalance(List<TimeDeposit>)` signature.

`InterestPolicy` is the base-interest extension point. `AnnualRateInterestPolicy` describes the existing formula with an annual rate and day window. To change a rate/window, change its registration in `TimeDepositCalculator`. To add a different base calculation, implement the interface and register it under a new plan name. The common first-30-days exclusion and original rounding apply only to base interest.

`BalanceAdjustment` is a separate extension point for independent credits and deductions. It receives an immutable `BalanceContext` containing the deposit ID, plan, days, opening balance, and already-rounded base interest. Every matching adjustment contributes a signed `BigDecimal` amount. Conditions and calculations use the same snapshot; they do not see balances changed by earlier adjustments. The decimal deltas are summed before one final conversion back to the protected model's double balance.

`ConditionalBalanceAdjustment` pairs a predicate with an amount calculation. Multiple criteria can be combined in that predicate. For example, an illustrative promotion can be registered as a Spring bean:

```java
@Bean
BalanceAdjustment higherBalancePromotion() {
  return new ConditionalBalanceAdjustment(
      context -> context.openingBalance().compareTo(new BigDecimal("1000.00")) > 0,
      context -> new BigDecimal("10.00"));
}
```

This example is not enabled in production. The application collects optional adjustment beans at startup, and the calculator takes an immutable copy of that list. The default list is empty, preserving the challenge's existing results. Direct Java callers can supply a list through the calculator constructor; no Spring dependency is needed in the calculation code.

All matching adjustments apply, so there is no single winning rule or priority ordering. Each rule returns its final decimal amount and owns any rule-specific rounding. Implementations should be side-effect-free. An adjustment can apply while base interest is zero if its own predicate allows it. Adjustments run on each successful update; once-only promotions would require separately specified persisted tracking.

The REST contract, persistence workflow, and transaction boundary are unchanged by registration of calculation rules.

The repository interface separates application orchestration from SQL. Domain policies have no Spring or database dependencies. Spring supplies dependency injection and the application transaction boundary; there is no additional framework or module hierarchy.

## Transactions and scale

Each update reads up to 500 rows at a time, in primary-key order, using `SELECT ... FOR UPDATE`. It calculates only after acquiring those locks and writes with JDBC batches. All batches share one transaction. Another application instance therefore waits and calculates from the committed balance, rather than overwriting a credit calculated from stale data. The request captures an upper ID bound at its start.

GET uses one ordered join, avoiding one query per deposit. The required full JSON list is materialized in memory. Updates have bounded application memory, but their locks remain held until the whole transaction ends. These are deliberate limits for a small synchronous exercise; very large datasets would justify streaming/pagination and an agreed background-processing contract.

## Test and build

```sh
./mvnw verify
```

Docker must be running. Testcontainers creates and removes its own PostgreSQL container; tests do not use the demonstration database.

To run the domain tests without Docker:

```sh
./mvnw -Dtest=TimeDepositCalculatorTest,BalanceAdjustmentTest test
```

The suite covers:

- Original rate/day boundaries, rounding, repeated calls, and unchanged metadata.
- Reconfiguring rate/window values and registering a different calculation.
- Stacking positive and negative adjustments, amount boundaries, combined criteria, and order-independent eligibility from the opening snapshot.
- Independent adjustment eligibility when base interest is zero, immutable startup registrations, and unchanged legacy rounding.
- Spring adjustment-bean discovery, decimal persistence of combined results, and rollback after an adjustment fails in a later batch.
- Real HTTP responses, decimal storage, withdrawal mapping, and the two-operation contract.
- Updates spanning more than one batch.
- Full rollback when a later batch overflows the database money column.
- Two overlapping HTTP updates: the test observes both waiting on PostgreSQL locks and checks that neither installment is lost.
- Foreign-key enforcement.

`verify` also creates the executable jar at `target/time-deposit-kata-1.0-SNAPSHOT.jar`.

## AI-assisted workflow

OpenAI Codex desktop assisted with requirements analysis, characterization tests, policy extraction, API/persistence implementation, integration tests, and documentation. Java, the small scope, and the extensibility/concurrency priorities were specified or discussed with the candidate.

The reproducible workflow was: read the supplied code and constraints; run characterization tests against the original calculator; make small changes; run unit and real PostgreSQL integration tests; inspect the final diff. The repository's [AGENTS.md](../AGENTS.md) contains the custom working rules. No additional agent framework or multi-agent workflow was used.

A concise task prompt that reproduces the intended scope:

> Implement the Java time-deposit exercise as a small, roughly one-hour solution. Preserve the shared model, calculator signature, rates, day boundaries, and rounding. Use a policy interface with reusable rate/window rules and optional independent balance adjustments evaluated against one immutable opening snapshot. Enable no adjustments without supplied business rules. Keep exactly two REST operations, PostgreSQL persistence, and atomic updates protected by database row locks. Verify boundaries, composition, rollback, and concurrent requests. Document assumptions and startup. Do not commit or push.

AI output still requires the candidate's own review and understanding before submission. Commits and pushes require explicit candidate authorization.
