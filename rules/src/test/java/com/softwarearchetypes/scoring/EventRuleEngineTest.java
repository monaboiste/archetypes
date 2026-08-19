package com.softwarearchetypes.scoring;

import com.softwarearchetypes.scoring.algebra.bool.BooleanAlgebra;
import com.softwarearchetypes.scoring.algebra.score.Score;
import com.softwarearchetypes.scoring.algebra.score.ScoreAlgebra;
import com.softwarearchetypes.scoring.ast.CmpOp;
import com.softwarearchetypes.scoring.ast.EventRule;
import com.softwarearchetypes.scoring.ast.Expression;
import com.softwarearchetypes.scoring.context.EventMetrics;
import com.softwarearchetypes.scoring.context.WindowContext;
import com.softwarearchetypes.scoring.customer.CustomerMetrics;
import com.softwarearchetypes.scoring.events.CustomerEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The notes' event-level example: "+3 for every purchase over 1,000". It only works if the filter is
// a real Boolean evaluated per event - the previous engine tested the window once and multiplied.
public class EventRuleEngineTest {

    private final EventRuleEngine engine = new EventRuleEngine(new BooleanAlgebra(), new ScoreAlgebra());

    @Test
    public void filtersEachEventOnItsOwnAmount() {
        Score score = engine.evaluateRules(List.of(pointsPerLargePurchase()), window(1500, 500, 2000));

        assertEquals(new Score(6), score); // two events over 1000, three points each
    }

    @Test
    public void aFilterThatDoesNotHoldScoresNothing() {
        EventRule neverApplies = new EventRule(
                new Expression.MetricCmp(EventMetrics.AMOUNT, CmpOp.GT, 100_000.0),
                new Expression.ConstScore(3));

        assertEquals(Score.ZERO, engine.evaluateRules(List.of(neverApplies), window(1500, 2000)));
    }

    @Test
    public void onlyTheMatchingRuleContributes() {
        EventRule smallPurchases = new EventRule(
                new Expression.MetricCmp(EventMetrics.AMOUNT, CmpOp.LT, 100.0),
                new Expression.ConstScore(50));

        Score score = engine.evaluateRules(List.of(pointsPerLargePurchase(), smallPurchases), window(1500));

        assertEquals(new Score(3), score);
    }

    @Test
    public void anEventMetricAndAWindowMetricCombineInOneFilter() {
        EventRule loyalCustomerBonus = new EventRule(
                new Expression.And(
                        new Expression.MetricCmp(EventMetrics.AMOUNT, CmpOp.GT, 1000.0),
                        new Expression.MetricCmp(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, CmpOp.GT, 10_000.0)),
                new Expression.ConstScore(3));

        assertEquals(new Score(3), engine.evaluateRules(List.of(loyalCustomerBonus), window(1500)));
        assertEquals(Score.ZERO, engine.evaluateRules(List.of(loyalCustomerBonus), poorWindow(1500)));
    }

    private static EventRule pointsPerLargePurchase() {
        return new EventRule(
                new Expression.MetricCmp(EventMetrics.AMOUNT, CmpOp.GT, 1000.0),
                new Expression.ConstScore(3));
    }

    private static WindowContext window(double... amounts) {
        return context(Map.of(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, 20_000.0), amounts);
    }

    private static WindowContext poorWindow(double... amounts) {
        return context(Map.of(CustomerMetrics.YEARLY_PURCHASE_AMOUNT, 500.0), amounts);
    }

    private static WindowContext context(Map<com.softwarearchetypes.scoring.ast.Metric, Double> metrics,
                                        double... amounts) {
        List<CustomerEvent> events = java.util.Arrays.stream(amounts)
                .mapToObj(amount -> new CustomerEvent("PURCHASE", Instant.EPOCH, amount))
                .toList();
        return new WindowContext("c-1", Instant.EPOCH, Instant.EPOCH, events, metrics);
    }
}
