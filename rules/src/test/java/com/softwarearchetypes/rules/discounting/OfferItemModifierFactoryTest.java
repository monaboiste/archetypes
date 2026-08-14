package com.softwarearchetypes.rules.discounting;


import com.softwarearchetypes.quantity.Quantity;
import com.softwarearchetypes.quantity.Unit;
import com.softwarearchetypes.quantity.money.Money;
import com.softwarearchetypes.rules.core.Modifier;
import com.softwarearchetypes.rules.core.config.RuleDefinitionRepository;
import com.softwarearchetypes.rules.core.config.reflection.ReflectionRuleConfigProvider;
import com.softwarearchetypes.rules.discounting.client.ClientContext;
import com.softwarearchetypes.rules.discounting.client.ClientContextRepository;
import com.softwarearchetypes.rules.discounting.client.ClientStatus;
import com.softwarearchetypes.rules.discounting.config.SampleStaticConfig;
import com.softwarearchetypes.rules.discounting.config.codecs.Codecs;
import com.softwarearchetypes.rules.discounting.offer.OfferItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OfferItemModifierFactoryTest {
    private ClientContextRepository clientContextRepository = new ClientContextRepository() {
        @Override
        public ClientContext loadClientContext(UUID clientId) {
            return new ClientContext(clientId, ClientStatus.VIP, Money.pln(1000000), LocalDate.of(2020, 1, 1));
        }
    };

    private RuleDefinitionRepository ruleRepository = new FakeRuleDefinitionRepository();

    @Test
    public void testVipDiscount() {
        OfferItem item = anyItemPriced(100);
        OfferItem modified = new OfferItemModifierFactory(null, null).createDiscountModifier2(ClientStatus.VIP).modify(item);
        assertEquals(anyItemPriced(85).getFinalPrice(), modified.getFinalPrice());
    }

    @Test
    public void testConfig(){
        OfferItemModifierFactory factory = new OfferItemModifierFactory(clientContextRepository, new SampleStaticConfig());

        Modifier<OfferItem> modifier = factory.createDiscountModifier3(UUID.randomUUID());
        var modified = modifier.modify(anyItemPriced(100));

        assertEquals(anyItemPriced(80).getFinalPrice(), modified.getFinalPrice());
    }


    @Test
    public void testReflectionDynamicConfig(){
        SampleStaticConfig config = new SampleStaticConfig();
        var rules = config.load();
        //save to DB
        ConfigImporter importer = new ConfigImporter(ruleRepository);
        importer.importConfig(rules);

        OfferItemModifierFactory factory = new OfferItemModifierFactory(clientContextRepository,
                new ReflectionRuleConfigProvider<>(ruleRepository, Codecs.QUANTITY));
        Modifier<OfferItem> modifier = factory.createDiscountModifier3(UUID.randomUUID());

        var modified = modifier.modify(anyItemPriced(100));
        assertEquals(anyItemPriced(80).getFinalPrice(), modified.getFinalPrice());
    }

    private OfferItem anyItemPriced(double amount) {
        return new OfferItem(UUID.randomUUID(), Quantity.of(1, Unit.kilograms()), Money.pln(amount));
    }

}
