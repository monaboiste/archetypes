package com.softwarearchetypes.accounting;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.softwarearchetypes.quantity.money.Money;

// Specification (L09): protect selected accounts without adding limit-specific state to Account.
final class NonNegativeBalanceConstraint implements TransactionEntriesConstraint {

    private final Set<AccountId> protectedAccounts;

    NonNegativeBalanceConstraint(Set<AccountId> protectedAccounts) {
        this.protectedAccounts = Set.copyOf(protectedAccounts);
    }

    @Override
    public String errorMessage() {
        return "Transaction would leave a protected account with a negative balance";
    }

    @Override
    public boolean test(Map<Entry, Account> entries) {
        Map<Account, Money> changes = new HashMap<>();
        entries.forEach((entry, account) -> {
            if (protectedAccounts.contains(account.id())) {
                changes.merge(account, entry.amount(), Money::add);
            }
        });
        // Transaction-wide validation (L09): use the net change, not each entry independently.
        return changes.entrySet().stream()
                .noneMatch(change -> change.getKey().balance().add(change.getValue()).isNegative());
    }
}
