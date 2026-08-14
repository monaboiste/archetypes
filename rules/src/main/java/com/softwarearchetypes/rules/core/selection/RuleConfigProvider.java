package com.softwarearchetypes.rules.core.selection;

import java.util.List;

// PATTERN: Abstract Factory over configuration. The rules may be hard-coded, computed at runtime
// from current data, or rebuilt from a database - the stable core does not care which.
// A List, not a Map: modifier order is business-significant and must not depend on hash iteration.
public interface RuleConfigProvider<C, T> {

    List<CandidateRule<C, T>> load();
}
    