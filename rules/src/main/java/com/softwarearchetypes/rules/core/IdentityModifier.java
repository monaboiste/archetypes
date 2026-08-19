package com.softwarearchetypes.rules.core;

// PATTERN: Null Object. Saves callers from a "no rule applies" branch.
public final class IdentityModifier<T> implements Modifier<T> {

    @Override
    public T modify(T subject) {
        return subject;
    }
}
    