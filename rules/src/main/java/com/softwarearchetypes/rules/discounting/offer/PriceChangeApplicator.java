package com.softwarearchetypes.rules.discounting.offer;

import com.softwarearchetypes.quantity.money.Money;
import com.softwarearchetypes.rules.core.ChangeApplicator;
import com.softwarearchetypes.rules.core.Modification;

// PATTERN: Adapter - the plugin side of the core's only domain seam. It teaches the generic
// ConfigurableModifier how to read and change the price of an OfferItem, which is why the core
// itself never mentions Money, prices or modifications.
public class PriceChangeApplicator implements ChangeApplicator<OfferItem, Money> {

    public static final PriceChangeApplicator INSTANCE = new PriceChangeApplicator();

    @Override
    public Money currentValue(OfferItem item) {
        return item.getFinalPrice();
    }

    @Override
    public OfferItem applyChange(OfferItem item, Money newPrice, String description) {
        // the item is a value object: it returns a new instance and records the modification,
        // so the offer can later explain how the final price came to be
        return item.apply(new Modification<>(newPrice, description));
    }
}
