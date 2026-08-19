package com.softwarearchetypes.rules.core;

// PATTERN: Strategy. One operation, many interchangeable implementations, named after the essence
// of a rule (something that transforms a subject) instead of today's example (a discount).
// This is the whole reusable contract of the rule core - everything else composes it.
public interface Modifier<T> {

    T modify(T subject);
}
    