package com.softwarearchetypes.scoring.algebra.fuzzy;

import com.softwarearchetypes.scoring.algebra.Algebra;
import com.softwarearchetypes.scoring.ast.ComparisonOperator;
import com.softwarearchetypes.scoring.ast.Metric;
import com.softwarearchetypes.scoring.context.MetricSource;

import java.util.List;
import java.util.Map;

public class FuzzyAlgebra implements Algebra<FuzzyValue> {

    private final Map<Metric, Double> margins;

    public FuzzyAlgebra(Map<Metric, Double> margins) {
        this.margins = margins;
    }

    @Override
    public FuzzyValue and(FuzzyValue left, FuzzyValue right) {
        return new FuzzyValue(Math.min(left.degree(), right.degree()));
    }

    @Override
    public FuzzyValue or(FuzzyValue left, FuzzyValue right) {
        return new FuzzyValue(Math.max(left.degree(), right.degree()));
    }

    @Override
    public FuzzyValue not(FuzzyValue inner) {
        return new FuzzyValue(1.0 - inner.degree());
    }

    @Override
    public FuzzyValue metricCmp(MetricSource source, Metric metric, ComparisonOperator op, double value) {
        double metricValue = source.metric(metric);
        double margin = margins.getOrDefault(metric, 0.0);

        if (margin <= 0.0) {
            boolean crisp = op.compare(metricValue, value);
            return new FuzzyValue(crisp ? 1.0 : 0.0);
        }

        return switch (op) {
            case GT -> fuzzyGreater(metricValue, value, margin);
            case GTE -> fuzzyGreater(metricValue, value - 0.1 * margin, margin);
            case LT -> fuzzyLess(metricValue, value, margin);
            case LTE -> fuzzyLess(metricValue, value + 0.1 * margin, margin);
            case EQ -> fuzzyEqual(metricValue, value, margin);
        };
    }

    private FuzzyValue fuzzyGreater(double v, double threshold, double margin) {
        if (v <= threshold) return new FuzzyValue(0.0);
        if (v >= threshold + margin) return new FuzzyValue(1.0);
        return new FuzzyValue((v - threshold) / margin);
    }

    private FuzzyValue fuzzyLess(double v, double threshold, double margin) {
        if (v >= threshold) return new FuzzyValue(0.0);
        if (v <= threshold - margin) return new FuzzyValue(1.0);
        return new FuzzyValue((threshold - v) / margin);
    }

    private FuzzyValue fuzzyEqual(double v, double target, double margin) {
        double diff = Math.abs(v - target);
        if (diff >= margin) return new FuzzyValue(0.0);
        return new FuzzyValue(1.0 - (diff / margin));
    }

    @Override
    public FuzzyValue constScore(int value) {
        // np. 0 → 0.0, reszta → 1.0
        return new FuzzyValue(value == 0 ? 0.0 : 1.0);
    }

    @Override
    public FuzzyValue sum(List<FuzzyValue> children) {
        double total = 0.0;
        for (FuzzyValue fuzzyValue : children) total += fuzzyValue.degree();
        if (total > 1.0) total = 1.0;
        return new FuzzyValue(total);
    }

    @Override
    public FuzzyValue ifThenElse(FuzzyValue cond, FuzzyValue thenValue, FuzzyValue elseValue) {
        double conditionDegree = cond.degree();
        double result = conditionDegree * thenValue.degree() + (1.0 - conditionDegree) * elseValue.degree();
        return new FuzzyValue(result);
    }
}
