package com.softwarearchetypes.rules.core.config;

import java.util.List;
import java.util.UUID;

// The relational shape from the notes: a rule table plus a parameter table.
// findAllDefinitions returns rules in a stable order, because rule order is business-significant.
public interface RuleDefinitionRepository {

    List<RuleDefinition> findAllDefinitions();

    List<RuleParam> findParamsByRuleId(UUID id);

    UUID insert(RuleDefinition definition);

    void insertParam(RuleParam param);
}
    