package com.softwarearchetypes.scoring.algebra;

import com.softwarearchetypes.scoring.ast.CmpOp;
import com.softwarearchetypes.scoring.ast.Metric;
import com.softwarearchetypes.scoring.context.MetricSource;

import java.util.List;

public interface Algebra<R> {

    R or(R left, R right);

    R and(R left, R right);

    R not(R inner);

    // The only operation that touches data: the bridge between an external metric source and the
    // logical AST. It takes a MetricSource, not a domain window, which is what keeps this whole
    // interface reusable for customers, suppliers, products or employees.
    R metricCmp(MetricSource source, Metric metric, CmpOp op, double value);

    R constScore(int value);

    R sum(List<R> list);

    R ifThenElse(R cond, R thenV, R elseV);

    default R label(String label, R inner) {
        // ignored in score and fuzzy
        return inner;
    }
}
