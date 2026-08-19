package com.softwarearchetypes.rules.core.config;

// Stable type keys and prefixes: persisted configuration must survive refactoring, so the two
// halves of a stored rule (what it does, and who it applies to) are addressed by fixed names.
public final class ConfigKeys {

    public static final String MODIFIER_PREFIX = "modifier";
    public static final String SELECTION_PREDICATE_PREFIX = "selectionPred";

    private ConfigKeys() {
    }
}
    