package com.softwarearchetypes.rules.discounting.offer.modifiers.simple;

import com.softwarearchetypes.quantity.money.Money;
import com.softwarearchetypes.quantity.money.Percentage;
import com.softwarearchetypes.rules.core.Modification;
import com.softwarearchetypes.rules.core.NamedModifier;
import com.softwarearchetypes.rules.discounting.OfferItemModifier;
import com.softwarearchetypes.rules.discounting.offer.OfferItem;

// A hand-written domain leaf on top of the core's NamedModifier: the percentage is a parameter,
// so one class covers the Standard / VIP / Gold cases instead of three identical subclasses.
public class PercentageOfferItemModifier extends NamedModifier<OfferItem> implements OfferItemModifier {
    private final Percentage percentage;

    public PercentageOfferItemModifier(String name, Percentage percentage) {
        super(name);
        this.percentage = percentage;
    }

    @Override
    public OfferItem modify(OfferItem item) {
        // percentage of the BASE price - an explicit choice, not an ambiguous method name
        Money modification = item.getBasePrice().multiply(percentage);
        Money newPrice = item.getBasePrice().subtract(modification);
        String description = getName() + " (" + percentage + "%)";

        return item.apply(new Modification<>(newPrice, description));
    }
}
