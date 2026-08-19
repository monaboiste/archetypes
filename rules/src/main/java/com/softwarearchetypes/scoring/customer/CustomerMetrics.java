package com.softwarearchetypes.scoring.customer;

import com.softwarearchetypes.scoring.ast.Metric;

// PATTERN: plugin over a reusable core. The customer scoring domain owns its metric vocabulary; the
// core knows only that a metric has a key. Constants keep the names discoverable and typo-free at
// the call site without closing the core type - a SupplierMetrics class scores suppliers with the
// very same AST, algebras and engine.
public final class CustomerMetrics {

    public static final Metric YEARLY_PURCHASE_AMOUNT = Metric.of("customer.yearly-purchase-amount");
    public static final Metric QUARTERLY_COMPLAINT_COUNT = Metric.of("customer.quarterly-complaint-count");
    public static final Metric LAST_PURCHASE_DAYS_AGO = Metric.of("customer.last-purchase-days-ago");

    private CustomerMetrics() {
    }
}
