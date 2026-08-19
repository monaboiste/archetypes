package com.softwarearchetypes.scoring;

import com.softwarearchetypes.scoring.algebra.bool.BooleanAlgebra;
import com.softwarearchetypes.scoring.algebra.explained.Contribution;
import com.softwarearchetypes.scoring.algebra.explained.ExplainableAlgebra;
import com.softwarearchetypes.scoring.algebra.explained.ExplainedScore;
import com.softwarearchetypes.scoring.algebra.explained.ExplainedScoreMonoid;
import com.softwarearchetypes.scoring.algebra.score.Score;
import com.softwarearchetypes.scoring.algebra.score.ScoreAlgebra;
import com.softwarearchetypes.scoring.algebra.score.ScoreMonoid;
import com.softwarearchetypes.scoring.ast.ComparisonOperator;
import com.softwarearchetypes.scoring.ast.EventRule;
import com.softwarearchetypes.scoring.ast.Expression;
import com.softwarearchetypes.scoring.ast.Metric;
import com.softwarearchetypes.scoring.customer.CustomerEvent;
import com.softwarearchetypes.scoring.customer.CustomerEventMetrics;
import com.softwarearchetypes.scoring.customer.CustomerMetrics;
import com.softwarearchetypes.scoring.customer.CustomerWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The notes' event-level example: "+3 for every purchase over 1,000". It only works if the filter is a
// real Boolean evaluated per event, and the last test shows the engine itself is indifferent to what a
// score even is.
public class EventRuleEngineTest {

    private final EventRuleEngine<Score> engine =
            new EventRuleEngine<>(new BooleanAlgebra(), new ScoreAlgebra(), new ScoreMonoid());

    @Test
    public void filtersEachEventOnItsOwnAmount() {
        Score score = engine.evaluateRules(List.of(pointsPerLargePurchase()), window(1500, 500, 2000));

        assertEquals(new Score(6), score); // two events over 1000, three points each
    }

    @Test
    public void aFilterThatDoesNotHoldScoresNothing() {
        EventRule neverApplies = new EventRule(
                new Expression.MetricComparison(CustomerEventMetrics.AMOUNT, ComparisonOperator.GT, 100_000.0),
                new Expression.ConstantScore(3));

        assertEquals(Score.ZERO, engine.evaluateRules(List.of(neverApplies), window(1500, 2000)));
    }

    @Test
    public void onlyTheMatchingRuleContributes() {
        EventRule smallPurchases = new EventRule(
                new Expression.MetricComparison(CustomerEventMetrics.AMOUNT, ComparisonOperator.LT, 100.0),
                new Expression.ConstantScore(50));

        Score score = engine.evaluateRules(List.of(pointsPerLargePurchase(), smallPurchases), window(1500));

        assertEquals(new Score(3), score);
    }

    @Test
    public void anEventMetricAndAWindowMetricCombineInOneFilter() {
        EventRule loyalCustomerBonus = new EventRule(
                new Expression.And(
                        new Expression.MetricComparison(CustomerEventMetrics.AMOUNT, ComparisonOperator.GT, 1000.0),
                        new Expression.MetricComparison(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, ComparisonOperator.GT, 10_000.0)),
                new Expression.ConstantScore(3));

        assertEquals(new Score(3), engine.evaluateRules(List.of(loyalCustomerBonus), window(1500)));
        assertEquals(Score.ZERO, engine.evaluateRules(List.of(loyalCustomerBonus), poorWindow(1500)));
    }

    // The event type is a 0/1 metric published by the plugin, so both halves of "+3 per PURCHASE over
    // 1,000" are ordinary numeric comparisons and the refund of 2000 scores nothing.
    @Test
    public void filtersOnTheEventType() {
        EventRule largePurchases = new EventRule(
                new Expression.And(
                        new Expression.MetricComparison(CustomerEventMetrics.typeIs("PURCHASE"), ComparisonOperator.EQ, 1.0),
                        new Expression.MetricComparison(CustomerEventMetrics.AMOUNT, ComparisonOperator.GT, 1000.0)),
                new Expression.ConstantScore(3));

        CustomerWindow window = mixedWindow(
                new CustomerEvent("PURCHASE", Instant.EPOCH, 1500),
                new CustomerEvent("REFUND", Instant.EPOCH, 2000),
                new CustomerEvent("PURCHASE", Instant.EPOCH, 500));

        assertEquals(new Score(3), engine.evaluateRules(List.of(largePurchases), window));
    }

    // Why L05.3 exists: the engine holds no Score. Swap the algebra and the monoid and the same rules
    // over the same window return an explanation instead of a number.
    @Test
    public void scoresWithAnyResultTypeNotJustScore() {
        EventRuleEngine<ExplainedScore> explaining = new EventRuleEngine<>(
                new BooleanAlgebra(), new ExplainableAlgebra(), new ExplainedScoreMonoid());
        EventRule labelled = new EventRule(
                new Expression.MetricComparison(CustomerEventMetrics.AMOUNT, ComparisonOperator.GT, 1000.0),
                new Expression.Labeled("large purchase", new Expression.ConstantScore(3)));

        ExplainedScore explained = explaining.evaluateRules(List.of(labelled), window(1500, 500, 2000));

        assertEquals(6, explained.total());
        assertEquals(List.of(new Contribution("large purchase", 3), new Contribution("large purchase", 3)),
                explained.contributions());
    }

    private static EventRule pointsPerLargePurchase() {
        return new EventRule(
                new Expression.MetricComparison(CustomerEventMetrics.AMOUNT, ComparisonOperator.GT, 1000.0),
                new Expression.ConstantScore(3));
    }

    private static CustomerWindow window(double... amounts) {
        return context(Map.of(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, 20_000.0), amounts);
    }

    private static CustomerWindow poorWindow(double... amounts) {
        return context(Map.of(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, 500.0), amounts);
    }

    private static CustomerWindow context(Map<Metric, Double> metrics, double... amounts) {
        List<CustomerEvent> events = java.util.Arrays.stream(amounts)
                .mapToObj(amount -> new CustomerEvent("PURCHASE", Instant.EPOCH, amount))
                .toList();
        return new CustomerWindow("c-1", Instant.EPOCH, Instant.EPOCH, events, metrics);
    }

    private static CustomerWindow mixedWindow(CustomerEvent... events) {
        return new CustomerWindow("c-1", Instant.EPOCH, Instant.EPOCH, List.of(events),
                Map.of(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, 20_000.0));
    }
}
