package com.softwarearchetypes.scoring;

import com.softwarearchetypes.scoring.algebra.Algebra;
import com.softwarearchetypes.scoring.algebra.AlgebraicVisitor;
import com.softwarearchetypes.scoring.algebra.score.Score;
import com.softwarearchetypes.scoring.ast.EventRule;
import com.softwarearchetypes.scoring.ast.ExpressionVisitor;
import com.softwarearchetypes.scoring.context.EventWindowContext;
import com.softwarearchetypes.scoring.context.WindowContext;
import com.softwarearchetypes.scoring.events.CustomerEvent;

import java.util.List;

// Two phases, two result types: the filter answers a Boolean, the score answers points. The engine
// no longer asks a scoring algebra whether a condition holds.
// ponytail: Score is still hardcoded as the scoring result, and CustomerEvent is still the event
// type - both are L05.3.
public class EventRuleEngine {

    private final Algebra<Boolean> filterAlgebra;
    private final Algebra<Score> scoreAlgebra;

    public EventRuleEngine(Algebra<Boolean> filterAlgebra, Algebra<Score> scoreAlgebra) {
        this.filterAlgebra = filterAlgebra;
        this.scoreAlgebra = scoreAlgebra;
    }

    public Score evaluateRules(List<EventRule> rules, WindowContext ctx) {
        Score total = Score.ZERO;
        for (CustomerEvent event : ctx.getEvents()) {
            // both visitors read the same per-event context, so a filter can talk about THIS event
            EventWindowContext evCtx = new EventWindowContext(ctx, event);
            ExpressionVisitor<Boolean> filterVisitor = new AlgebraicVisitor<>(evCtx, filterAlgebra);
            ExpressionVisitor<Score> scoreVisitor = new AlgebraicVisitor<>(evCtx, scoreAlgebra);
            for (EventRule rule : rules) {
                if (Boolean.TRUE.equals(rule.filterExpr().accept(filterVisitor))) {
                    total = total.plus(rule.scoreExpr().accept(scoreVisitor));
                }
            }
        }
        return total;
    }
}
