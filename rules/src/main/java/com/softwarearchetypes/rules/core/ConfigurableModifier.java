package com.softwarearchetypes.rules.core;

import java.util.function.Function;
import java.util.function.Predicate;

// The generalized rule: three configurable concerns instead of one hard-coded algorithm.
//   predicate - does the subject qualify?
//   applier   - what is the new value?
//   guardian  - is the proposed result allowed?
// PATTERN: Strategy per concern, composed by configuration - no subclass per business case.
// The guardian is checked against a simulated candidate, which keeps a business invariant in one
// shared place instead of scattering emergency ifs over every caller.
public class ConfigurableModifier<T, V> extends NamedModifier<T> {

    private final Predicate<T> predicate;
    private final Function<T, V> applier;
    private final Predicate<T> guardian;
    private final ChangeApplicator<T, V> applicator;

    public ConfigurableModifier(String name,
                                Predicate<T> predicate,
                                Function<T, V> applier,
                                Predicate<T> guardian,
                                ChangeApplicator<T, V> applicator) {
        super(name);
        this.predicate = predicate;
        this.applier = applier;
        this.guardian = guardian;
        this.applicator = applicator;
    }

    @Override
    public T modify(T subject) {
        if (!predicate.test(subject)) {
            return subject;
        }

        V newValue = applier.apply(subject);
        if (newValue.equals(applicator.currentValue(subject))) {
            return subject; // nothing changed, return the very same instance
        }

        T candidate = applicator.applyChange(subject, newValue, getName());
        return guardian.test(candidate) ? candidate : subject;
    }

    // Accessors, not decoration: the reflection configuration writer reads constructor
    // parameters back through them when a rule is persisted as data.
    public Predicate<T> getPredicate() {
        return predicate;
    }

    public Function<T, V> getApplier() {
        return applier;
    }

    public Predicate<T> getGuardian() {
        return guardian;
    }

    public ChangeApplicator<T, V> getApplicator() {
        return applicator;
    }
}
