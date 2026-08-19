package com.softwarearchetypes.scoring.context;

import java.util.List;

// PATTERN: port. A window is a domain concept - which events, which projections, which time span,
// which subject - so the core asks only for the one projection it actually walks: a metric source per
// event. Whoever owns the domain decides what each event exposes.
public interface EventWindow {

    List<MetricSource> events();
}
