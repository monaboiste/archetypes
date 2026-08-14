package com.softwarearchetypes.rules.discounting;

import com.softwarearchetypes.quantity.Quantity;
import com.softwarearchetypes.quantity.Unit;
import com.softwarearchetypes.quantity.money.Money;
import com.softwarearchetypes.quantity.money.Percentage;
import com.softwarearchetypes.rules.core.ChainModifier;
import com.softwarearchetypes.rules.core.ConfigurableModifier;
import com.softwarearchetypes.rules.discounting.offer.OfferItem;
import com.softwarearchetypes.rules.discounting.offer.PriceChangeApplicator;
import com.softwarearchetypes.rules.discounting.offer.modifiers.functors.applier.Amount;
import com.softwarearchetypes.rules.discounting.offer.modifiers.functors.guardians.MarginGuardian;
import com.softwarearchetypes.rules.discounting.offer.modifiers.functors.predicates.MoreExpensiveThanPredicate;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ChainOfferModifierTest {
    @Test
    public void testOneMod() {
        ChainModifier<OfferItem> modifier = new ChainModifier<>();
        modifier.add(new ConfigurableModifier<OfferItem, Money>(
                "expensive line",
                new MoreExpensiveThanPredicate(Money.pln(1000)),
                new Amount(Money.pln(100)),
                new MarginGuardian(Percentage.of(30)),
                PriceChangeApplicator.INSTANCE
        ));

        var item = modifier.modify(anyItemPriced(2000));
        assertEquals(anyItemPriced(1900).getFinalPrice(), item.getFinalPrice());
    }

    @Test
    public void testGuardian() {
        ChainModifier<OfferItem> modifier = new ChainModifier<>();
        modifier.add(new ConfigurableModifier<OfferItem, Money>(
                "expensive line",
                new MoreExpensiveThanPredicate(Money.pln(1000)),
                new Amount(Money.pln(900)),
                new MarginGuardian(Percentage.of(60)),
                PriceChangeApplicator.INSTANCE
        ));

        var item = modifier.modify(anyItemPriced(2000));
        assertEquals(anyItemPriced(2000).getFinalPrice(), item.getFinalPrice());
    }

    private OfferItem anyItemPriced(double amount) {
        return new OfferItem(UUID.randomUUID(), Quantity.of(1, Unit.kilograms()), Money.pln(amount));
    }
}
