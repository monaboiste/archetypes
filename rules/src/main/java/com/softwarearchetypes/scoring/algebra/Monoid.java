package com.softwarearchetypes.scoring.algebra;

// PATTERN: Monoid - an identity and an associative combine. Folding one result per matched rule into
// a total is the only thing the engine does with R, so it is the only thing the engine needs to know
// about R. Naming that concept is what lets Score leave the general logic: the engine no longer knows
// that Score.ZERO or Score.plus exist.
public interface Monoid<R> {

    R zero();

    R combine(R left, R right);
}
