package com.softwarearchetypes.rules.discounting;

import com.softwarearchetypes.rules.core.config.RuleDefinition;
import com.softwarearchetypes.rules.core.config.RuleDefinitionRepository;
import com.softwarearchetypes.rules.core.config.RuleParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class FakeRuleDefinitionRepository implements RuleDefinitionRepository {
    private final List<RuleDefinition> definitions = new ArrayList<>();
    private final List<RuleParam> params = new ArrayList<>();

    @Override
    public List<RuleDefinition> findAllDefinitions() {
        return definitions;
    }

    @Override
    public List<RuleParam> findParamsByRuleId(UUID id) {
        return params.stream()
                .filter(p -> Objects.equals(p.ruleId(), id))
                .toList();
    }

    @Override
    public UUID insert(RuleDefinition definition) {
        RuleDefinition stored = new RuleDefinition(UUID.randomUUID(), definition.name());
        definitions.add(stored);
        return stored.id();
    }

    @Override
    public void insertParam(RuleParam param) {
        params.add(param);
    }
}
