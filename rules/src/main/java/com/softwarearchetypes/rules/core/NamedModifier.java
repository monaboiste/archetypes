package com.softwarearchetypes.rules.core;

// The name of a rule is data, not a type: one parameterized class replaces a
// VipDiscount / GoldDiscount / StandardDiscount class hierarchy whose algorithm was identical.
public abstract class NamedModifier<T> implements Modifier<T> {

    private final String name;

    protected NamedModifier(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
    