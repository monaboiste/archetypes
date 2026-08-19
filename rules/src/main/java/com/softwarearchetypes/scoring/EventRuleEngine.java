package com.softwarearchetypes.scoring;

import com.softwarearchetypes.scoring.algebra.Algebra;
import com.softwarearchetypes.scoring.algebra.AlgebraicVisitor;
import com.softwarearchetypes.scoring.algebra.Monoid;
import com.softwarearchetypes.scoring.ast.EventRule;
import com.softwarearchetypes.scoring.ast.ExpressionVisitor;
import com.softwarearchetypes.scoring.context.EventWindow;
import com.softwarearchetypes.scoring.context.MetricSource;

import java.util.List;

// The general logic, and nothing but: two phases with two result types - the filter answers a Boolean,
// the score answers R - and a Monoid to fold the matches. R may be points, an explanation or a fuzzy
// value; the window and its events belong to whichever domain supplied them.
public class EventRuleEngine<R> {

    private final Algebra<Boolean> filterAlgebra;
    private final Algebra<R> scoreAlgebra;
    private final Monoid<R> monoid;

    public EventRuleEngine(Algebra<Boolean> filterAlgebra, Algebra<R> scoreAlgebra, Monoid<R> monoid) {
        this.filterAlgebra = filterAlgebra;
        this.scoreAlgebra = scoreAlgebra;
        this.monoid = monoid;
    }

    public R evaluateRules(List<EventRule> rules, EventWindow window) {
        R total = monoid.zero();
        for (MetricSource event : window.events()) {
            // both visitors read the same per-event source, so a filter can talk about THIS event
            ExpressionVisitor<Boolean> filterVisitor = new AlgebraicVisitor<>(event, filterAlgebra);
            ExpressionVisitor<R> scoreVisitor = new AlgebraicVisitor<>(event, scoreAlgebra);
            for (EventRule rule : rules) {
                if (rule.filterExpr().accept(filterVisitor)) {
                    total = monoid.combine(total, rule.scoreExpr().accept(scoreVisitor));
                }
            }
        }
        return total;
    }
}
