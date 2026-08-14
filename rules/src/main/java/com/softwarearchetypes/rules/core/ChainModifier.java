package com.softwarearchetypes.rules.core;

import java.util.ArrayList;
import java.util.List;

// PATTERN: Composite. A collection of modifiers adapted to the Modifier interface itself, so a
// caller cannot tell one rule from many. Preferred over Chain of Responsibility here: no successor
// field has to be bolted onto every reusable modifier. Order is business-significant - hence a List.
public class ChainModifier<T> implements Modifier<T> {

    private final List<Modifier<T>> modifiers = new ArrayList<>();

    @Override
    public T modify(T subject) {
        for (Modifier<T> modifier : modifiers) {
            subject = modifier.modify(subject); // a fold over the list
        }
        return subject;
    }

    public ChainModifier<T> add(Modifier<T> modifier) {
        modifiers.add(modifier);
        return this;
    }
}
    