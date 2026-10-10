# Holds and limits using the Accounting archetype

## Scope

Executable configuration examples are the scenario tests listed below. Production
extensions remain generic constraints, ledger views, allocation queries and rule
execution fixes. No Hold, Reservation or Limit entity/table was added.

Use PLN and the course's operational signs: debit decreases, credit increases.
Builder inputs are positive magnitudes; debit entries expose negative amounts.
This is not a statutory chart of accounts.

## Accounts

| Account role                                   | Type        | Balance means                                |
| ---------------------------------------------- | ----------- | -------------------------------------------- |
| `CUSTOMER:123:WALLET`                          | LIABILITY   | Available customer money                     |
| `CUSTOMER:123:HELD`                            | LIABILITY   | Authorized money not yet settled or returned |
| `MERCHANT:456:SETTLEMENT`                      | LIABILITY   | Money settled to the merchant                |
| `CUSTOMER:123:DAILY:2026-10-08:AVAILABLE/USED` | OFF_BALANCE | Remaining/committed daily allowance          |
| `CUSTOMER:123:MONTHLY:2026-10:AVAILABLE/USED`  | OFF_BALANCE | Remaining/committed monthly allowance        |
| `BANK:LIMIT_GRANTS`                            | OFF_BALANCE | Technical source of allowances               |
| `CUSTOMER:123:AUTHORIZATION_REQUESTS`          | OFF_BALANCE | Recorded requested amounts                   |
| `CUSTOMER:123:LIMIT_DECISIONS`                 | OFF_BALANCE | Approval, rejection and breach audit amounts |

Account IDs provide identity; names select customer/role/period. Customer ownership
and authorization are caller responsibilities, not inferred security guarantees.
Entry metadata records customer, authorization reference, reason and related
transaction IDs. External authorization references are not idempotency keys.

## Transactions and postings

Examples use a 100 PLN hold and a 40 PLN partial settlement.

| Event/type                     | Debit                                                     | Credit                                                                | Link                                                                         |
| ------------------------------ | --------------------------------------------------------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `LIMIT_GRANTED`                | LIMIT_GRANTS                                              | Period AVAILABLE                                                      | Customer, period and kind metadata                                           |
| `AUTHORIZATION_REQUESTED`, 100 | none                                                      | REQUESTS 100                                                          | Request TransactionId                                                        |
| `HOLD_AUTHORIZED`, 100         | WALLET 100; daily AVAILABLE 100; monthly AVAILABLE 100    | HELD 100; daily USED 100; monthly USED 100; DECISIONS 100             | Request/authorization metadata; held credit EntryId                          |
| `HOLD_SETTLED`, 40             | HELD 40                                                   | SETTLEMENT 40                                                         | Held debit `appliedTo` original held credit                                  |
| `HOLD_RELEASED`, 60            | HELD 60; original daily USED 60; original monthly USED 60 | WALLET 60; original daily AVAILABLE 60; original monthly AVAILABLE 60 | Debits allocate to original held/usage credits                               |
| `EXPIRATION_COMPENSATION`, 60  | HELD 60                                                   | WALLET 60                                                             | Source entry reference and original transaction `refId`                      |
| `LIMIT_ATTEMPT_REJECTED`, 600  | none                                                      | DECISIONS 600                                                         | Request ID, requested amount, limits, availability, usage, excess and reason |
| `LIMIT_BREACHED`               | none                                                      | DECISIONS                                                             | Source override/import transaction and observed overrun                      |
| `REVERSAL`                     | Opposite of original credits                              | Opposite of original debits                                           | Original transaction `refId`; each entry references its immediate original   |

Money postings balance independently of informational postings. Audit amounts and
daily/monthly usage must not be added to customer money. An audit account's balance
is a sum of amounts, not an event count.

Authorization consumes both allowances. Settlement does not consume them again.
Cancellation returns only unused commitments, including after rollover: refunds
belong to the original periods. New periods get new accounts/grants; old balances
are never reset in place. Use Europe/Warsaw calendar boundaries and half-open
`[start, end)` intervals, including 23/25-hour DST days. Credit limits are omitted.

Hold validity belongs to its credit entry. Crossing the deadline alone changes no
balance. Expiry compensation returns the current remainder; fully consumed sources
need no posting. Scheduling and coordinating associated allowance refunds are
upstream responsibilities, not automatic behavior in the supplied expiry builder.

## Rules and consistency

- **Command/Builder/Factory:** explicit transactions group the money and informational
  changes. An approval and its audit entry are posted together.
- **Specifications:** balancing, selected-account nonnegative balances, allocation,
  expiry and reversal constraints run before execution as well as during building.
  Account-local validation remains private to `Account.addEntries`. An in-memory
  unit of work stages postings on detached account copies, then installs their
  prepared states only after every account accepts its entries. Live account
  identities remain stable for prebuilt commands and pending events stay isolated.
- **Allocation:** source remainder sums same-account reference descendants, including
  corrections of consumption. Ancestor capacity/validity prevents a reversal credit
  from becoming a second independent allowance or extending an expired hold.
- **Reversal:** preserve originals and metadata; add correction reasons. Undo active
  dependent settlements/releases before reversing a full hold. Reinstating a reversed
  settlement requires capacity not already reused by another operation.
- **Posting Rules:** scenario configuration composes same-entry EligibilityCondition,
  AccountFinder and calculation. Requests are recorded before manual decision firing.
  Rejection changes only the audit ledger; failed processing leaves a queryable pending
  request. Actual-breach rules observe current balances when invoked.
- **Identity/history:** facade execution rejects duplicate transaction IDs and
  unrecorded reversal references. Transaction posting collections are immutable.

## Ledger reads

- Available money: WALLET. Held money: HELD. Their sum excludes already settled money.
- Active holds: held credits with positive remaining allocation, including expired
  credits awaiting compensation. Follow the reference graph, not only direct debits.
- Period utilization: the period's USED balance as of reporting time, including late
  corrections. Posting-window reports instead filter `appliesAt`; their net movement
  can be negative when corrections concern older periods.
- Audit: select DECISIONS by customer, decision and source transaction metadata;
  consult reversal links to distinguish historical decisions from their corrections.
- Pending requests: REQUESTS without a recorded matching decision. A successful
  empty rule run is not an authorization decision.
- Projections select original entries and IDs using both account and entry predicates.

## Verification and limits

Scenario classes in `accounting/src/test/java/com/softwarearchetypes/accounting/`:
`FundsHoldScenarios`, `FundsHoldSettlementScenarios`, `FundsHoldReleaseScenarios`,
`TransactionLimitScenarios`, `LimitAuditScenarios`, `AccountingReversibilityScenarios`,
`AccountPostingScenarios`.

```sh
./gradlew test
```

Guarantees are sequential and in-memory. Failed domain validation does not partially
post a transaction; a batch stops at failure but retains earlier successful transactions
for explicit compensation. Persistence/publication failures, concurrent writers,
durable outbox, automatic expiry orchestration and historical-time solvency enforcement
require additional infrastructure/policy. Constraints and customer/period routing are
explicit configuration, not mandatory implicit behavior of every generic transfer.
