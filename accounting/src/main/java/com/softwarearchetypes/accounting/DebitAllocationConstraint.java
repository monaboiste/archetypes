package com.softwarearchetypes.accounting;

import java.util.HashMap;
import java.util.Map;

import com.softwarearchetypes.quantity.money.Money;

// Specification (L09): validate transaction-wide consumption separately from allocation queries.
final class DebitAllocationConstraint implements TransactionEntriesConstraint {

    private final EntryAllocations entryAllocations;

    DebitAllocationConstraint(EntryAllocations entryAllocations) {
        this.entryAllocations = entryAllocations;
    }

    @Override
    public String errorMessage() {
        return "Debit allocation requires a positive amount within the remaining valid credit on the same account and currency";
    }

    @Override
    public boolean test(Map<Entry, Account> entries) {
        Map<EntryId, Money> consumption = new HashMap<>();
        for (Entry entry : entries.keySet()) {
            if (!(entry instanceof AccountDebited debit) || debit.appliedTo().isEmpty()) {
                continue;
            }
            Entry source = entryAllocations.findSource(debit.appliedTo().orElseThrow()).orElse(null);
            if (!(source instanceof AccountCredited credit)
                    || !credit.accountId().equals(debit.accountId())
                    || !credit.amount().currency().equals(debit.amount().currency())
                    || !debit.amount().isNegative()
                    || debit.appliesAt().isBefore(credit.appliesAt())
                    || !credit.validity().isValidAt(debit.appliesAt())) {
                return false;
            }
            // Allocation (L06): all debits using one source share its remaining value.
            Money consumed = consumption.merge(credit.id(), debit.amount().negate(), Money::add);
            if (consumed.isGreaterThan(entryAllocations.remainingAmount(credit))) {
                return false;
            }
        }
        return true;
    }
}
