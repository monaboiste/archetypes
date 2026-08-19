package com.softwarearchetypes.scoring.context;

import com.softwarearchetypes.scoring.ast.Metric;

// Metric keys describing "the event being scored right now" rather than the whole window. Generic on
// purpose - an event has an amount whatever the business domain is - so the core stays free of any
// domain vocabulary.
public final class EventMetrics {

    public static final Metric AMOUNT = Metric.of("EVENT_AMOUNT");

    private EventMetrics() {
    }
}
