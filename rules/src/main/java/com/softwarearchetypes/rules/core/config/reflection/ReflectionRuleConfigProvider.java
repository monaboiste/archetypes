package com.softwarearchetypes.rules.core.config.reflection;

import com.softwarearchetypes.rules.core.Modifier;
import com.softwarearchetypes.rules.core.config.ConfigKeys;
import com.softwarearchetypes.rules.core.config.RuleDefinition;
import com.softwarearchetypes.rules.core.config.RuleDefinitionRepository;
import com.softwarearchetypes.rules.core.config.RuleParam;
import com.softwarearchetypes.rules.core.selection.CandidateRule;
import com.softwarearchetypes.rules.core.selection.RuleConfigProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// The dynamic half of the configuration: the reusable building blocks stay in code, while which
// blocks are active and with what parameters is data. Was ReflectionDynamicConfig, tied to discounts.
public class ReflectionRuleConfigProvider<C, T> implements RuleConfigProvider<C, T> {

    private final RuleDefinitionRepository repository;
    private final List<ValueCodec> codecs;

    public ReflectionRuleConfigProvider(RuleDefinitionRepository repository, List<ValueCodec> codecs) {
        this.repository = repository;
        this.codecs = List.copyOf(codecs);
    }

    @Override
    public List<CandidateRule<C, T>> load() {
        List<CandidateRule<C, T>> rules = new ArrayList<>();

        for (RuleDefinition definition : repository.findAllDefinitions()) {
            Map<String, String> params = toParamMap(repository.findParamsByRuleId(definition.id()));
            ReflectionBeanReader reader = new ReflectionBeanReader(params, codecs);

            // reflection is limited to instantiating a stable type key and casting it to a known
            // interface - the two halves of a rule: what it does, and who it applies to
            @SuppressWarnings("unchecked")
            Modifier<T> modifier = (Modifier<T>) reader.readBean(ConfigKeys.MODIFIER_PREFIX, Modifier.class);
            @SuppressWarnings("unchecked")
            Predicate<C> appliesTo = (Predicate<C>) reader.readBean(ConfigKeys.SELECTION_PREDICATE_PREFIX, Predicate.class);

            rules.add(new CandidateRule<>(modifier, appliesTo));
        }

        return rules;
    }

    private Map<String, String> toParamMap(List<RuleParam> params) {
        return params.stream().collect(Collectors.toMap(RuleParam::paramName, RuleParam::paramValue));
    }
}
    