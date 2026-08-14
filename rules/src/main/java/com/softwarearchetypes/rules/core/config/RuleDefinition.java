package com.softwarearchetypes.rules.core.config;

import java.util.UUID;

// User-authored rules are stored as DATA, never as source code: one row per rule with a stable
// identity, plus rows of parameters. Renamed from Discount - the store knows nothing about discounts.
public record RuleDefinition(UUID id, String name) {
}
    