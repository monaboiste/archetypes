package com.softwarearchetypes.rules.core.predicates;

// Fluent combinators that build Specification nodes: business leaves stay small and single-purpose,
// while the tree they form is still explicit data rather than an opaque lambda.
public interface RichLogicalPredicate<T> extends LogicalPredicate<T> {
    default LogicalPredicate<T> and(LogicalPredicate<T> other) {
        return new AndPredicate<>(this, other);
    }

    default LogicalPredicate<T> or(LogicalPredicate<T> other) {
        return new OrPredicate<>(this, other);
    }

    default LogicalPredicate<T> not() {
        return new NotPredicate<>(this);
    }
}
    