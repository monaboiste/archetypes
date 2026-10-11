package com.softwarearchetypes.accounting;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.softwarearchetypes.accounting.events.AccountingEvent;
import com.softwarearchetypes.accounting.events.CreditEntryRegistered;
import com.softwarearchetypes.accounting.events.DebitEntryRegistered;
import com.softwarearchetypes.common.Version;
import com.softwarearchetypes.quantity.money.Money;

import static com.softwarearchetypes.common.Preconditions.checkArgument;

class Account {

    private final AccountId accountId;
    private final AccountType type;
    private final AccountName name;
    private Money balance;
    //for optimistic locking
    private final Version version;
    //we do not load all accounts' entries when retrieving them from DB
    //adding new entries requires optimistic lock
    private Entries newEntries;
    private List<AccountingEvent> pendingEvents = new LinkedList<>();

    Account(AccountId accountId, AccountType type, AccountName name) {
        this(accountId, type, name, Version.initial());
    }

    Account(AccountId accountId, AccountType type, AccountName name, Version version) {
        this(accountId, type, name, Money.zeroPln(), version);
    }

    Account(AccountId accountId, AccountType type, AccountName name, Money balance, Version version) {
        checkArgument(accountId != null, "Account ID must be defined");
        checkArgument(type != null, "Account type must be defined");
        checkArgument(name != null, "Account name must be defined");
        checkArgument(version != null, "Account version must be defined");
        this.accountId = accountId;
        this.type = type;
        this.name = name;
        this.version = version;
        this.balance = Optional.ofNullable(balance).orElse(Money.zeroPln());
        this.newEntries = Entries.empty();
    }

    void addEntry(Entry entry) {
        addEntries(List.of(entry));
    }

    private void validateEntries(List<Entry> entries) {
        // Aggregate invariants (L03/L09): only Account decides whether it can accept its entries.
        for (Entry entry : entries) {
            checkArgument(accountId.equals(entry.accountId()), "Entry must belong to the receiving account");
            checkArgument(balance.currency().equals(entry.amount().currency()), "Entry currency must match account currency");
        }
    }

    void addEntries(List<Entry> newEntries) {
        validateEntries(newEntries);
        this.newEntries.addAll(newEntries);
        newEntries.forEach(entry -> {
            balance = balance.add(entry.amount());
            recordEntryEvent(entry);
        });
    }

    StagedPosting stageEntries(List<Entry> entries) {
        // Unit of Work: apply normal aggregate behavior to detached state, including pending events.
        Account staged = new Account(accountId, type, name, balance, version);
        staged.newEntries = newEntries.copy();
        staged.pendingEvents.addAll(pendingEvents);
        staged.addEntries(entries);
        return new StagedPosting(staged);
    }

    final class StagedPosting {
        private final Account staged;
        private boolean committed;

        private StagedPosting(Account staged) {
            this.staged = staged;
        }

        void commit() {
            checkArgument(!committed, "Staged posting has already been committed");
            balance = staged.balance;
            newEntries = staged.newEntries;
            pendingEvents = staged.pendingEvents;
            committed = true;
        }
    }

    String name() {
        return name.value();
    }

    private void recordEntryEvent(Entry entry) {
        AccountingEvent event = switch (entry) {
            case AccountCredited credited -> new CreditEntryRegistered(
                    UUID.randomUUID(),
                    entry.occurredAt(),
                    entry.appliesAt(),
                    entry.id().value(),
                    entry.accountId().uuid(),
                    entry.transactionId().value(),
                    entry.amount()
            );
            case AccountDebited debited -> new DebitEntryRegistered(
                    UUID.randomUUID(),
                    entry.occurredAt(),
                    entry.appliesAt(),
                    entry.id().value(),
                    entry.accountId().uuid(),
                    entry.transactionId().value(),
                    entry.amount()
            );
        };
        pendingEvents.add(event);
    }

    List<AccountingEvent> getPendingEvents() {
        return List.copyOf(pendingEvents);
    }

    void clearPendingEvents() {
        pendingEvents.clear();
    }

    AccountId id() {
        return accountId;
    }

    Entries entries() {
        return newEntries.copy();
    }

    AccountType type() {
        return type;
    }

    Money balance() {
        return balance;
    }

    Version version() {
        return version;
    }

}


