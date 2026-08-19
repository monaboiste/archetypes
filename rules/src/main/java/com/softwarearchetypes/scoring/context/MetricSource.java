package com.softwarearchetypes.scoring.context;

import com.softwarearchetypes.scoring.ast.Metric;

// PATTERN: Adapter / port - the same trick as ChangeApplicator in rules.core: one narrow seam
// through which generic code reads domain data. metricCmp is the bridge between external data and
// the logical AST, and hiding that bridge behind a single method is what keeps the AST and the
// algebras independent of the data representation. A projection, a map, a test lambda: all equal.
@FunctionalInterface
public interface MetricSource {

    double metric(Metric metric);
}
