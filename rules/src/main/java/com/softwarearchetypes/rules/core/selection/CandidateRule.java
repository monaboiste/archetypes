package com.softwarearchetypes.rules.core.selection;

import com.softwarearchetypes.rules.core.Modifier;

import java.util.function.Predicate;

// Rule selection is kept separate from rule execution. appliesTo may only look at data available
// at selection time (the selection context C); the modifier works on the subject T and sees the
// subject context only. Two different contexts, two different phases.
public record CandidateRule<C, T>(Modifier<T> modifier, Predicate<C> appliesTo) {
}
    