package com.softwarearchetypes.rules.discounting.config;

import com.softwarearchetypes.quantity.money.Money;
import com.softwarearchetypes.quantity.money.Percentage;
import com.softwarearchetypes.rules.core.ConfigurableModifier;
import com.softwarearchetypes.rules.core.Modifier;
import com.softwarearchetypes.rules.core.selection.CandidateRule;
import com.softwarearchetypes.rules.discounting.client.ClientContext;
import com.softwarearchetypes.rules.discounting.client.ClientStatus;
import com.softwarearchetypes.rules.discounting.client.rules.ExpensesRule;
import com.softwarearchetypes.rules.discounting.client.rules.StatusRule;
import com.softwarearchetypes.rules.discounting.client.rules.TimeBeingCustomer;
import com.softwarearchetypes.rules.discounting.offer.OfferItem;
import com.softwarearchetypes.rules.discounting.offer.PriceChangeApplicator;
import com.softwarearchetypes.rules.discounting.offer.modifiers.functors.applier.PercentageFromBase;
import com.softwarearchetypes.rules.discounting.offer.modifiers.functors.guardians.EmptyGuardian;
import com.softwarearchetypes.rules.discounting.offer.modifiers.functors.predicates.MoreExpensiveThanPredicate;

import java.util.List;
import java.util.function.Predicate;

// The simplest configuration: the rule map lives in code. Every rule is assembled from reusable
// core blocks, so a change here is a change of data, not of an algorithm.
public class SampleStaticConfig implements ConfigProvider {

    @Override
    public List<CandidateRule<ClientContext, OfferItem>> load() {
        Modifier<OfferItem> mod1 = new ConfigurableModifier<OfferItem, Money>(
                "3 years of VIPs",
                new MoreExpensiveThanPredicate(Money.pln(50)),
                new PercentageFromBase(Percentage.of(10)),
                EmptyGuardian.INSTANCE,
                PriceChangeApplicator.INSTANCE);
        // selection predicate: a Specification tree over data known before pricing
        Predicate<ClientContext> pred1 = StatusRule.of(ClientStatus.VIP).and(TimeBeingCustomer.ofYears(3));

        Modifier<OfferItem> mod2 = new ConfigurableModifier<OfferItem, Money>(
                "VIPs - big fish",
                new MoreExpensiveThanPredicate(Money.pln(100)),
                new PercentageFromBase(Percentage.of(10)),
                EmptyGuardian.INSTANCE,
                PriceChangeApplicator.INSTANCE);
        Predicate<ClientContext> pred2 = StatusRule.of(ClientStatus.VIP).and(ExpensesRule.of(Money.pln(500000)));

        // a List, not a Map: the order in which the modifiers run is part of the business decision
        return List.of(
                new CandidateRule<>(mod1, pred1),
                new CandidateRule<>(mod2, pred2));
    }
}
