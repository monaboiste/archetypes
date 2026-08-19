package com.softwarearchetypes.scoring.customer;

import com.softwarearchetypes.scoring.ast.Metric;

import java.util.Map;

// What a single customer event contributes to a metric lookup. The plugin, not the core, decides which
// facts of an event are visible to rules - that is the whole point of the MetricSource port.
public final class CustomerEventMetrics {

    public static final Metric AMOUNT = Metric.of("EVENT_AMOUNT");

    // A 0/1 metric per event type keeps type filtering inside the numeric comparison the AST already
    // has, so the core needs neither a string comparison nor a domain-aware node.
    public static Metric typeIs(String type) {
        return Metric.of("EVENT_TYPE_IS_" + type);
    }

    public static Map<Metric, Double> of(CustomerEvent event) {
        return Map.of(
                AMOUNT, event.amount(),
                typeIs(event.type()), 1.0);
    }

    private CustomerEventMetrics() {
    }
}
