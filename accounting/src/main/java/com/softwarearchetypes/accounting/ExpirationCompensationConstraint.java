package com.softwarearchetypes.accounting;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.softwarearchetypes.quantity.money.Money;

// Specification (L09): expiry compensation has different timing rules from ordinary consumption.
final class ExpirationCompensationConstraint implements TransactionEntriesConstraint {

    private final Entry source;
    private final EntryAllocations entryAllocations;
    private final Clock clock;

    ExpirationCompensationConstraint(Entry source, EntryAllocations entryAllocations, Clock clock) {
        this.source = source;
        this.entryAllocations = entryAllocations;
        this.clock = clock;
    }

    @Override
    public String errorMessage() {
        return "Expiration compensation must offset the current remaining amount at or after the source expires";
    }

    @Override
    public boolean test(Map<Entry, Account> entries) {
        if (!source.validity().hasExpired(clock.instant())) {
            return false;
        }
        List<Entry> sourceAccountPostings = entries.keySet().stream()
                .filter(entry -> entry.accountId().equals(source.accountId())).toList();
        if (sourceAccountPostings.size() != 1) {
            return false;
        }
        Entry compensation = sourceAccountPostings.getFirst();
        if (compensation.appliedTo().filter(source.id()::equals).isEmpty()
                || !source.validity().hasExpired(compensation.appliesAt())
                || compensation.appliesAt().isBefore(source.appliesAt())
                || compensation.getClass().equals(source.getClass())) {
            return false;
        }
        // Compensation (L06): re-read the remainder so stale or duplicate commands cannot return value twice.
        Money remaining = entryAllocations.remainingAmount(source);
        return !remaining.isZero()
                && remaining.isNegative() == source.amount().isNegative()
                && compensation.amount().equals(remaining.negate());
    }
}
