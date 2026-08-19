package com.softwarearchetypes.scoring.algebra.explained;

import com.softwarearchetypes.scoring.algebra.Algebra;
import com.softwarearchetypes.scoring.ast.ComparisonOperator;
import com.softwarearchetypes.scoring.ast.Metric;
import com.softwarearchetypes.scoring.context.MetricSource;

import java.util.ArrayList;
import java.util.List;

public class ExplainableAlgebra implements Algebra<ExplainedScore> {

    @Override
    public ExplainedScore and(ExplainedScore left, ExplainedScore right) {
        int result = Math.min(left.total(), right.total());
        return new ExplainedScore(result, merge(left, right));
    }

    @Override
    public ExplainedScore or(ExplainedScore left, ExplainedScore right) {
        int result = Math.max(left.total(), right.total());
        return new ExplainedScore(result, merge(left, right));
    }

    @Override
    public ExplainedScore not(ExplainedScore inner) {
        int result = inner.total() > 0 ? 0 : 1;
        return new ExplainedScore(result, inner.contributions());
    }

    @Override
    public ExplainedScore metricCmp(MetricSource source, Metric metric, ComparisonOperator op, double value) {
        double metricValue = source.metric(metric);
        boolean matches = op.compare(metricValue, value);
        return new ExplainedScore(matches ? 1 : 0, List.of());
    }

    @Override
    public ExplainedScore constScore(int value) {
        return new ExplainedScore(value, List.of());
    }

    @Override
    public ExplainedScore sum(List<ExplainedScore> children) {
        int total = 0;
        List<Contribution> all = new ArrayList<>();
        for (ExplainedScore explainedScore : children) {
            total += explainedScore.total();
            all.addAll(explainedScore.contributions());
        }
        return new ExplainedScore(total, all);
    }

    @Override
    public ExplainedScore ifThenElse(ExplainedScore cond,
                                     ExplainedScore thenValue,
                                     ExplainedScore elseValue) {
        return cond.total() > 0 ? thenValue : elseValue;
    }

    @Override
    public ExplainedScore label(String label, ExplainedScore inner) {
        List<Contribution> list = new ArrayList<>(inner.contributions());
        if (inner.total() != 0) {
            list.add(new Contribution(label, inner.total()));
        }
        return new ExplainedScore(inner.total(), list);
    }

    private List<Contribution> merge(ExplainedScore left, ExplainedScore right) {
        List<Contribution> merged = new ArrayList<>(left.contributions());
        merged.addAll(right.contributions());
        return merged;
    }
}
