package com.softwarearchetypes.accounting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.softwarearchetypes.quantity.money.Money;

// Specification (L09): a full reversal must respect outstanding uses and the allocation ancestry.
final class ReversalConstraint implements TransactionEntriesConstraint {

    private final List<Entry> originals;
    private final EntryAllocations entryAllocations;

    ReversalConstraint(Transaction original, EntryAllocations entryAllocations) {
        this.originals = original.entries().values().stream().flatMap(List::stream).toList();
        this.entryAllocations = entryAllocations;
    }

    @Override
    public String errorMessage() {
        return "Reversal requires unconsumed original entries, valid effective time and sufficient ancestor allocation";
    }

    @Override
    public boolean test(Map<Entry, Account> entries) {
        if (entries.size() != originals.size()) {
            return false;
        }
        Map<Entry, Money> ancestorChanges = new HashMap<>();
        for (Entry original : originals) {
            List<Entry> counterparts = entries.keySet().stream()
                    .filter(entry -> entry.appliedTo().filter(original.id()::equals).isPresent()).toList();
            if (counterparts.size() != 1) {
                return false;
            }
            Entry opposite = counterparts.getFirst();
            if (!opposite.accountId().equals(original.accountId())
                    || !opposite.amount().equals(original.amount().negate())
                    || opposite.appliesAt().isBefore(original.appliesAt())
                    || !entryAllocations.remainingAmount(original).equals(original.amount())) {
                return false;
            }
            for (Entry ancestor : entryAllocations.ancestorsOf(original)) {
                ancestorChanges.merge(ancestor, opposite.amount(), Money::add);
            }
        }
        // Reversal of reversal (L05/L06): reinstating a use must not spend already-reused source capacity.
        return ancestorChanges.entrySet().stream().allMatch(change -> {
            Money remaining = entryAllocations.remainingAmount(change.getKey()).add(change.getValue());
            return remaining.isZero() || remaining.isNegative() == change.getKey().amount().isNegative();
        });
    }
}
