package com.softwarearchetypes.rules.discounting;

import com.softwarearchetypes.quantity.money.Percentage;
import com.softwarearchetypes.rules.core.IdentityModifier;
import com.softwarearchetypes.rules.core.Modifier;
import com.softwarearchetypes.rules.core.selection.CandidateRule;
import com.softwarearchetypes.rules.core.selection.RuleConfigProvider;
import com.softwarearchetypes.rules.core.selection.RuleSelector;
import com.softwarearchetypes.rules.discounting.client.ClientContext;
import com.softwarearchetypes.rules.discounting.client.ClientContextRepository;
import com.softwarearchetypes.rules.discounting.client.ClientStatus;
import com.softwarearchetypes.rules.discounting.offer.OfferItem;
import com.softwarearchetypes.rules.discounting.offer.modifiers.simple.PercentageOfferItemModifier;

import java.util.List;
import java.util.UUID;

public class OfferItemModifierFactory {

    private final ClientContextRepository clientContextRepository;
    // the general core type, so a static config, a runtime-computed one, or a database-backed
    // reflection provider can all be plugged in without touching this class
    private final RuleConfigProvider<ClientContext, OfferItem> configProvider;


    public OfferItemModifierFactory(ClientContextRepository clientContextRepository,
                                    RuleConfigProvider<ClientContext, OfferItem> configProvider) {
        this.clientContextRepository = clientContextRepository;
        this.configProvider = configProvider;
    }

    // Variant 1: the naive switch. Fine while statuses are fixed, expensive once a new status
    // means hunting down every switch in the system.
    public Modifier<OfferItem> createDiscountModifier(ClientStatus status) {
        switch (status) {
            case STANDARD:
                return new PercentageOfferItemModifier("My friend", Percentage.ofFraction(0.05));
            case VIP:
                return new PercentageOfferItemModifier("VIP", Percentage.ofFraction(0.15));
            case GOLD:
                return new PercentageOfferItemModifier("Gold", Percentage.of(25));
            default:
                return new IdentityModifier<>();
        }
    }

    // Variant 2: PATTERN Visitor over a closed enum - the compiler now points at every place that
    // must handle a new status. Useless the moment statuses become user-defined data.
    public OfferItemModifier createDiscountModifier2(ClientStatus status) {
        return status.accept(new OfferItemModifierVisitor());
    }

    // Variant 3: the generalized path. Two stages, both in the core: select the rules that apply to
    // this client, then let the returned chain run them against each item.
    public Modifier<OfferItem> createDiscountModifier3(UUID clientId) {
        List<CandidateRule<ClientContext, OfferItem>> rules = configProvider.load();
        ClientContext clientContext = clientContextRepository.loadClientContext(clientId);

        return RuleSelector.select(clientContext, rules);
    }
}
