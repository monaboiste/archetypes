package com.softwarearchetypes.scoring.ast;

import java.util.Objects;

// PATTERN: Value Object over a stable type key. A metric used to be an enum constant, which forced
// every business domain into the core. Now the metric identity is data, not a type ("the name is
// data, not a type"), so adding a metric never means editing the general code. The key is what gets
// persisted in a serialized rule tree, so it must be stable across refactoring - never a class name.
public record Metric(String key) {

    public Metric {
        Objects.requireNonNull(key, "metric key required");
        if (key.isBlank()) {
            throw new IllegalArgumentException("blank metric key");
        }
    }

    // Keys arrive from user-authored configuration, so this factory is the validation boundary.
    public static Metric of(String key) {
        return new Metric(key);
    }

    @Override
    public String toString() {
        return key;
    }
}
