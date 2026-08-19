package com.softwarearchetypes.scoring.context;

import com.softwarearchetypes.scoring.ast.Metric;
import com.softwarearchetypes.scoring.events.CustomerEvent;

// PATTERN: Decorator over the MetricSource port. The event being scored overlays the window metrics
// and falls through to them for anything it does not answer, so one expression can mix
// a metric of this event with a metric of the whole window. Without this the per-event loop in
// EventRuleEngine had nothing per-event to test.
// ponytail: still names CustomerEvent - generalizing the event type is L05.3.
public class EventWindowContext extends WindowContext {

    private final CustomerEvent currentEvent;

    public EventWindowContext(WindowContext base, CustomerEvent currentEvent) {
        super(base.getCustomerId(), base.getFrom(), base.getTo(), base.getEvents(), base.getMetrics());
        this.currentEvent = currentEvent;
    }

    public CustomerEvent getCurrentEvent() {
        return currentEvent;
    }

    @Override
    public double metric(Metric metric) {
        if (EventMetrics.AMOUNT.equals(metric)) {
            return currentEvent.amount();
        }
        return super.metric(metric);
    }
}
