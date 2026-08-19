package com.softwarearchetypes.scoring;

import com.softwarearchetypes.scoring.algebra.AlgebraicVisitor;
import com.softwarearchetypes.scoring.algebra.score.Score;
import com.softwarearchetypes.scoring.algebra.score.ScoreAlgebra;
import com.softwarearchetypes.scoring.ast.ComparisonOperator;
import com.softwarearchetypes.scoring.ast.Expression;
import com.softwarearchetypes.scoring.ast.Metric;
import com.softwarearchetypes.scoring.context.MetricSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The point of L05.1: this is a scoring core, not a customer-scoring core. The same AST, visitor and
// algebra score a supplier, with metrics the core has never heard of, no window class and no domain
// event - the MetricSource is a one-line lambda.
public class MetricGeneralizationTest {

    private static final Metric ON_TIME_DELIVERY_RATE = Metric.of("on-time-delivery-rate");
    private static final Metric OPEN_DISPUTES = Metric.of("open-disputes");

    @Test
    public void scoresADomainTheCoreNeverHeardOf() {
        MetricSource supplier = metrics(Map.of(
                ON_TIME_DELIVERY_RATE, 0.98,
                OPEN_DISPUTES, 4.0));

        Score score = supplierRule().accept(new AlgebraicVisitor<>(supplier, new ScoreAlgebra()));

        assertEquals(new Score(20), score);
    }

    @Test
    public void anAbsentMetricScoresAsZero() {
        MetricSource nothingKnown = metrics(Map.of());

        Score score = supplierRule().accept(new AlgebraicVisitor<>(nothingKnown, new ScoreAlgebra()));

        assertEquals(Score.ZERO, score);
    }

    @Test
    public void sameKeyMeansSameMetric() {
        assertEquals(ON_TIME_DELIVERY_RATE, Metric.of("on-time-delivery-rate"));
    }

    private static MetricSource metrics(Map<Metric, Double> values) {
        return metric -> values.getOrDefault(metric, 0.0);
    }

    private Expression supplierRule() {
        return new Expression.Sum(List.of(
                new Expression.IfThenElse(
                        new Expression.MetricComparison(ON_TIME_DELIVERY_RATE, ComparisonOperator.GT, 0.95),
                        new Expression.ConstantScore(50),
                        new Expression.ConstantScore(0)),
                new Expression.IfThenElse(
                        new Expression.MetricComparison(OPEN_DISPUTES, ComparisonOperator.GT, 3.0),
                        new Expression.ConstantScore(-30),
                        new Expression.ConstantScore(0))));
    }
}
