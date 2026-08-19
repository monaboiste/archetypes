package com.softwarearchetypes.scoring;

import com.softwarearchetypes.scoring.algebra.AlgebraicVisitor;
import com.softwarearchetypes.scoring.algebra.bool.BooleanAlgebra;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The same AST the scoring tests use, interpreted as plain logic: a filter phase deserves its own
// result type rather than borrowing Score's 0/1 convention.
public class BooleanAlgebraTest {

    private static final Metric AMOUNT = Metric.of("AMOUNT");

    private static final MetricSource SOURCE = metric -> Map.of(AMOUNT, 1500.0).getOrDefault(metric, 0.0);

    @Test
    public void comparesAMetricWithoutADetourThroughPoints() {
        assertTrue(eval(above(1000)));
        assertFalse(eval(above(2000)));
    }

    @Test
    public void andOrNotObeyTheUsualLaws() {
        assertTrue(eval(new Expression.And(above(1000), above(1400))));
        assertFalse(eval(new Expression.And(above(1000), above(2000))));
        assertTrue(eval(new Expression.Or(above(2000), above(1000))));
        assertFalse(eval(new Expression.Or(above(2000), above(3000))));
        assertFalse(eval(new Expression.Not(above(1000))));
    }

    @Test
    public void ifThenElsePicksABranch() {
        assertTrue(eval(new Expression.IfThenElse(above(1000), above(1400), above(9000))));
        assertTrue(eval(new Expression.IfThenElse(above(9000), above(1400), above(1000))));
    }

    @Test
    public void sumIsDisjunction() {
        assertTrue(eval(new Expression.Sum(List.of(above(9000), above(1000)))));
        assertFalse(eval(new Expression.Sum(List.of(above(9000), above(8000)))));
        assertFalse(eval(new Expression.Sum(List.of())));
    }

    @Test
    public void constantIsFalseOnlyWhenZero() {
        assertTrue(eval(new Expression.ConstantScore(50)));
        assertTrue(eval(new Expression.ConstantScore(-30)));
        assertFalse(eval(new Expression.ConstantScore(0)));
    }

    // Why the task exists: a scoring algebra used as a filter reads a negative subtree as "condition
    // not met", so Not(-30) came out true. Boolean logic gets it right.
    @Test
    public void negativePointsNoLongerReadAsFalse() {
        Expression filter = new Expression.Not(new Expression.ConstantScore(-30));

        Score asScore = filter.accept(new AlgebraicVisitor<>(SOURCE, new ScoreAlgebra()));

        assertEquals(new Score(1), asScore);
        assertTrue(asScore.value() > 0);   // the old filtering convention: "condition met"
        assertFalse(eval(filter));         // the Boolean algebra: -30 is truthy, so Not(-30) is false
    }

    private static Expression above(double threshold) {
        return new Expression.MetricComparison(AMOUNT, ComparisonOperator.GT, threshold);
    }

    private static boolean eval(Expression expression) {
        return expression.accept(new AlgebraicVisitor<>(SOURCE, new BooleanAlgebra()));
    }
}
