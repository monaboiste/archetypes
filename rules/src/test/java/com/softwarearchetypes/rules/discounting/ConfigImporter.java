package com.softwarearchetypes.rules.discounting;

import com.softwarearchetypes.rules.core.Modifier;
import com.softwarearchetypes.rules.core.config.ConfigKeys;
import com.softwarearchetypes.rules.core.config.RuleDefinition;
import com.softwarearchetypes.rules.core.config.RuleDefinitionRepository;
import com.softwarearchetypes.rules.core.config.RuleParam;
import com.softwarearchetypes.rules.core.config.reflection.ReflectionBeanWriter;
import com.softwarearchetypes.rules.core.selection.CandidateRule;
import com.softwarearchetypes.rules.discounting.client.ClientContext;
import com.softwarearchetypes.rules.discounting.config.codecs.Codecs;
import com.softwarearchetypes.rules.discounting.offer.OfferItem;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class ConfigImporter  {

    private final RuleDefinitionRepository ruleRepository;
    private final ReflectionBeanWriter beanWriter = new ReflectionBeanWriter(Codecs.QUANTITY);

    public ConfigImporter(RuleDefinitionRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public void importConfig(List<CandidateRule<ClientContext, OfferItem>> rules) {
        for (CandidateRule<ClientContext, OfferItem> rule : rules) {
            Modifier<OfferItem> modifier = rule.modifier();

            Map<String, String> params = new HashMap<>();
            beanWriter.writeBean(ConfigKeys.MODIFIER_PREFIX, modifier, params);

            String name = humanReadableName(modifier);

            UUID ruleId = ruleRepository.insert(new RuleDefinition(null, name));
            for (Map.Entry<String, String> p : params.entrySet()) {
                ruleRepository.insertParam(new RuleParam(ruleId, p.getKey(), p.getValue()));
            }

            params.clear();
            Predicate<ClientContext> appliesTo = rule.appliesTo();

            beanWriter.writeBean(ConfigKeys.SELECTION_PREDICATE_PREFIX, appliesTo, params);
            for (Map.Entry<String, String> p : params.entrySet()) {
                ruleRepository.insertParam(new RuleParam(ruleId, p.getKey(), p.getValue()));
            }
        }
    }

    private String humanReadableName(Modifier<OfferItem> modifier) {
        try {
            Method m = modifier.getClass().getMethod("getName");
            Object result = m.invoke(modifier);
            if (result != null) {
                return result.toString();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return modifier.getClass().getSimpleName();
    }
}
