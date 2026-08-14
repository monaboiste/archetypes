package com.softwarearchetypes.rules.core;

// What a rule did and why: the new value plus its explanation. A core concept, not a discount one -
// every domain plugged into the core needs to justify its results to users and support.
public record Modification<V>(V amount, String description) {
}
