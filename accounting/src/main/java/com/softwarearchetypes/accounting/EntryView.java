package com.softwarearchetypes.accounting;

import java.time.Instant;
import java.util.Optional;

import com.softwarearchetypes.quantity.money.Money;

public record EntryView(EntryId entryId,
                        EntryType type,
                        Money amount,
                        TransactionId transactionId,
                        AccountId accountId,
                        Instant occurredAt,
                        Instant appliesAt,
                        MetaData metadata,
                        Optional<EntryId> appliedTo,
                        Validity validity) {

    public EntryView(EntryId entryId, EntryType type, Money amount, TransactionId transactionId,
                     AccountId accountId, Instant occurredAt, Instant appliesAt) {
        this(entryId, type, amount, transactionId, accountId, occurredAt, appliesAt, MetaData.empty());
    }

    public EntryView(EntryId entryId, EntryType type, Money amount, TransactionId transactionId,
                     AccountId accountId, Instant occurredAt, Instant appliesAt, MetaData metadata) {
        this(entryId, type, amount, transactionId, accountId, occurredAt, appliesAt, metadata, Optional.empty());
    }

    public EntryView(EntryId entryId, EntryType type, Money amount, TransactionId transactionId,
                     AccountId accountId, Instant occurredAt, Instant appliesAt, MetaData metadata,
                     Optional<EntryId> appliedTo) {
        this(entryId, type, amount, transactionId, accountId, occurredAt, appliesAt, metadata, appliedTo, Validity.always());
    }

    //intentionally left package-scoped
    static EntryView from(Entry entry) {
        return new EntryView(entry.id(),
                EntryType.from(entry),
                entry.amount(),
                entry.transactionId(),
                entry.accountId(),
                entry.occurredAt(),
                entry.appliesAt(),
                // Read model (L08): expose the original entry's context, not a separate audit record.
                entry.metadata(),
                // Allocation (L06): retain the source-entry link so reads can reconstruct consumption.
                entry.appliedTo(),
                // Validity (L06): readers can discover deadlines without inferring them from posting times.
                entry.validity());
    }

    public enum EntryType {
        CREDIT,
        DEBIT;

        static EntryType from(Entry entry) {
            return switch (entry) {
                case AccountCredited c -> CREDIT;
                case AccountDebited d -> DEBIT;
            };
        }
    }
}
