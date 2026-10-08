# Accounting - Practical Assignment

## Assignment: Modeling Limits and Funds Holds in Electronic Banking

### Context

You already have a working accounting system in which:

- Customers have accounts (e.g., WALLET, CREDIT, and others).
- You record transactions and their associated entries (`Entry`).
- You have mechanisms for automating postings (`PostingRule`) and can build views/projections based on the recorded
  entries.

The product team raises a business need:

> We need to add transaction limits and holds (as in electronic banking: card authorization, daily/monthly limits,
> available credit limit, etc.).

At the same time, a strict architectural constraint applies:

> We do not introduce new domain entities (no new tables/entities such as `Limit`, `Reservation`, `Hold`, etc.).
> You work exclusively with what already exists: `Accounts`, `Transactions`, `Entries`, `PostingRules`, etc.

## Objective and Functional Requirements

Design (and/or configure) an extension to the existing accounting system that supports funds holds and transaction
limits without adding new domain entities. The solution must rely exclusively on `Accounts`, `Transactions`, `Entries`,
and rules (`PostingRule`/`EligibilityCondition`/`AccountFinder`) and meet the following requirements:

1. **Placing funds on hold before settlement**

   The system must allow a hold for a specified amount to be recorded for a customer (e.g., during authorization),
   before the actual settlement takes place. The hold must be visible in the ledger and uniquely identifiable (it must
   be possible to determine who, how much, and for what reason).

2. **Settling an operation linked to a hold**

   It must be possible to perform a settlement linked to an earlier hold, so that the sequence of events can be
   reconstructed from the accounting data: hold → settlement (including partial settlement, if it occurs).

3. **Releasing/canceling a hold and handling expiration**

   A hold must be releasable when the operation is canceled or when the hold expires after a period of time.
   Release/expiration must be handled through accounting entries (without deleting history) and leave a complete audit
   trail.

4. **Daily/monthly limits and credit limits (if applicable)**

   The model must allow transaction limit usage to be recorded (at least daily and monthly limits) and its utilization
   within a specified time window to be read from ledger entries. If you include a credit limit, it should be possible
   to show its availability/utilization over time using the same mechanism.

5. **Auditing limit breaches and attempted breaches**

   The system must allow situations in which a limit was exceeded (or an attempt was made to exceed it) to be audited,
   so that they can be read from the accounting records, with no “silent” decisions outside the recording system.

6. **Reversibility and accounting consistency**

   Each of the above processes must be reversible and reconstructable from the data: without manually “correcting”
   records in place and without deleting them. Transactions must maintain accounting consistency and balance (except for
   intentionally used informational entries, if your system supports them).

## Constraints (Mandatory)

- Do not add new domain entities: no new entities/tables such as `Limit`, `Reservation`, `Hold`, etc.
- Use only:
  - `Account` (including different account types, if available),
  - `Transaction` and `Entry`,
  - `PostingRule` (posting automation),
  - `EligibilityCondition` (conditional rule execution),
  - `AccountFinder` (account selection based on context/tags/types),
  - existing mechanisms for reading balances and building views/projections.

## Scope and Format of the Solution

Submit a short design document (text plus optional diagrams) that is unambiguous and complete. It must include:

### 1. Accounts and Their Roles

- Which accounts (or classes of accounts) are needed for:
  - funds holds,
  - recording limit utilization,
  - (optionally) auditing limit breaches,
  - (optionally) the available credit limit.
- What the balance of each account means (business interpretation).
- How you distinguish a customer's accounts (e.g., by type/tag/context) without introducing new entities.

### 2. Transaction Types and Business Events

Define a set of transaction types (names and meanings), covering at least:

- creating a hold,
- settlement linked to a hold,
- releasing/canceling a hold,
- recording limit utilization,
- resetting/renewing a limit over time,
- recording limit breaches/attempted breaches (if they occur).

### 3. Posting Flows (Entries)

Use examples (a table or list) to show which entries are created and which accounts are involved in at least the
following scenarios:

- hold → settlement,
- hold → cancellation/release,
- an expired hold (and how it is handled),
- an operation that consumes a limit (daily or monthly),
- an attempt to exceed a limit (how it is recorded/audited).

Each flow must clearly indicate:

- which account is debited/credited,
- the amount posted,
- the business meaning of the entry,
- how events are linked over time (e.g., “this settlement relates to this hold”).

### 4. Reversibility and History

Describe how your model handles:

- reversing an operation (reversal),
- corrections (compensation),
- partial settlements,
- expiration of holds,

so that the history is complete and can be reconstructed solely from accounting entries.

### 5. Views/Queries (What Can Be Calculated from the Ledger)

Explain, at a conceptual level, how the following can be derived from the data:

- available funds vs. funds on hold,
- limit utilization within a daily/monthly window,
- a list of active holds,
- limit breach/attempted breach events.

## Evaluation Criteria

The following will be assessed:

- Thinking in accounting terms (value is recorded and settled, not “set”).
- Maintaining balance and consistency of entries.
- Full auditability (each step leaves a trail).
- Reversibility (no deletions, no “silent” corrections).
- Adherence to the constraints (no new domain entities).
- Clarity of the model: the sequence of events can be reconstructed from the ledger alone.
