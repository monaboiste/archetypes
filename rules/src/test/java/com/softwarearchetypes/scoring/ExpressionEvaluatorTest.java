package com.softwarearchetypes.scoring;

import com.softwarearchetypes.scoring.algebra.AlgebraicVisitor;
import com.softwarearchetypes.scoring.algebra.score.Score;
import com.softwarearchetypes.scoring.algebra.score.ScoreAlgebra;
import com.softwarearchetypes.scoring.algebra.score.simplified.ExpressionEvaluator;
import com.softwarearchetypes.scoring.algebra.score.simplified.ScoringAlgebra;
import com.softwarearchetypes.scoring.algebra.score.simplified.SimpleScoringAlgebra;
import com.softwarearchetypes.scoring.ast.ComparisonOperator;
import com.softwarearchetypes.scoring.ast.Expression;
import com.softwarearchetypes.scoring.ast.Metric;
import com.softwarearchetypes.scoring.customer.CustomerMetrics;
import com.softwarearchetypes.scoring.customer.CustomerWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ExpressionEvaluatorTest {

    @Test
    public void simplified_ScoringAlgebraTimeWindowTest(){
        CustomerWindow customerWindow = window(
                Map.of(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, 20000.0,
                        CustomerMetrics.QUARTERLY_COMPLAINT_COUNT, 5.0));
        Expression rule = yearlyAndQuarterlyRule();
        ScoringAlgebra algebra = new SimpleScoringAlgebra();

        Score score = ExpressionEvaluator.eval(rule, customerWindow, algebra);

        assertEquals(new Score(20), score);
    }

    @Test
    public void scoringAlgebraTimeWindowTest(){
        CustomerWindow customerWindow = window(
                Map.of(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, 20000.0,
                        CustomerMetrics.QUARTERLY_COMPLAINT_COUNT, 5.0));
        Expression rule = yearlyAndQuarterlyRule();
        AlgebraicVisitor<Score> visitor = new AlgebraicVisitor<>(customerWindow, new ScoreAlgebra());

        Score score = rule.accept(visitor);

        assertEquals(new Score(20), score);
    }

    private CustomerWindow window(Map<Metric, Double> metrics) {
        return new CustomerWindow("c-1", Instant.EPOCH, Instant.EPOCH, List.of(), metrics);
    }

    private Expression yearlyAndQuarterlyRule() {
        Expression highTurnoverRule = new Expression.IfThenElse(
                new Expression.MetricComparison(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, ComparisonOperator.GT, 10_000.0),
                new Expression.ConstantScore(50),
                new Expression.ConstantScore(0)
        );

        Expression tooManyComplaintsRule = new Expression.IfThenElse(
                new Expression.MetricComparison(CustomerMetrics.QUARTERLY_COMPLAINT_COUNT, ComparisonOperator.GT, 3.0),
                new Expression.ConstantScore(-30),
                new Expression.ConstantScore(0)
        );

        return new Expression.Sum(List.of(
                highTurnoverRule,
                tooManyComplaintsRule
        ));
    }
}
