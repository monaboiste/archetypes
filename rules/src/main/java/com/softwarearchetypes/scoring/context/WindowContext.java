package com.softwarearchetypes.scoring.context;

import com.softwarearchetypes.scoring.ast.Metric;
import com.softwarearchetypes.scoring.events.CustomerEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Implements the core MetricSource port, so an algebra sees only "give me this metric" and never
// this customer-shaped window. ponytail: customerId and the CustomerEvent list still live here -
// generalizing them is L05.3, not this task.
public class WindowContext implements MetricSource {

    private final String customerId;
    private final Instant from;
    private final Instant to;
    private final List<CustomerEvent> events;
    private final Map<Metric, Double> metrics;

    public WindowContext(String customerId,
                         Instant from,
                         Instant to,
                         List<CustomerEvent> events,
                         Map<Metric, Double> metrics) {
        this.customerId = customerId;
        this.from = from;
        this.to = to;
        this.events = events;
        this.metrics = metrics;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Instant getFrom() {
        return from;
    }

    public Instant getTo() {
        return to;
    }

    public List<CustomerEvent> getEvents() {
        return events;
    }

    public Map<Metric, Double> getMetrics() {
        return metrics;
    }

    @Override
    public double metric(Metric metric) {
        return metrics.getOrDefault(metric, 0.0);
    }
}