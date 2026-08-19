package com.softwarearchetypes.scoring.context;

import com.softwarearchetypes.scoring.ast.Metric;

import java.util.Map;

// PATTERN: Decorator over the MetricSource port. The metrics of the event being scored shadow the
// window's, and anything the event does not answer falls through, so one expression can mix per-event
// and per-window metrics. Absence is a missing key rather than a zero: an event whose amount really
// is 0 must not silently read the window's value.
public record MetricOverlay(Map<Metric, Double> extra, MetricSource fallback) implements MetricSource {

    public MetricOverlay {
        extra = Map.copyOf(extra);
    }

    @Override
    public double metric(Metric metric) {
        Double value = extra.get(metric);
        return value != null ? value : fallback.metric(metric);
    }
}
