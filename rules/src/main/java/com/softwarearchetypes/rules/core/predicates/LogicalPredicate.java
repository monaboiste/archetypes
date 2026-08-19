package com.softwarearchetypes.rules.core.predicates;

import java.util.function.Predicate;

// PATTERN: Specification, explicit-node flavour. A marker for predicates that form an inspectable
// tree. Explicit And / Or / Not nodes beat lambdas composed with Predicate.and() because their
// operands can be walked, visualized, serialized and rebuilt by the configuration store.
public interface LogicalPredicate<T> extends Predicate<T> {
}
    