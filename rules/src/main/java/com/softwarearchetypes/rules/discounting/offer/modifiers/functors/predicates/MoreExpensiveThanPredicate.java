package com.softwarearchetypes.rules.discounting.offer.modifiers.functors.predicates;

import com.softwarearchetypes.quantity.money.Money;
import com.softwarearchetypes.rules.core.predicates.RichLogicalPredicate;
import com.softwarearchetypes.rules.discounting.offer.OfferItem;


public class MoreExpensiveThanPredicate implements RichLogicalPredicate<OfferItem> {
    private final Money amount;

    public MoreExpensiveThanPredicate(Money amount) {
        this.amount = amount;
    }

    @Override
    public boolean test(OfferItem offerItem) {
        return offerItem.getBasePrice().isGreaterThanOrEqualTo(amount);
    }

    public Money getAmount() {
        return amount;
    }
}
