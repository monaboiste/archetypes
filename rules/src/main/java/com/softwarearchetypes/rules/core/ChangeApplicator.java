package com.softwarearchetypes.rules.core;

// PATTERN: Adapter. The single seam through which the generic core touches a domain aggregate:
// how to read the current value, and how to produce a new immutable subject carrying a new value
// plus its explanation. Keeping the adapter here (rather than making the aggregate implement a core
// interface) is exactly what lets the core stay ignorant of OfferItem, Money or any other domain type.
public interface ChangeApplicator<T, V> {

    V currentValue(T subject);

    T applyChange(T subject, V newValue, String description);
}
    