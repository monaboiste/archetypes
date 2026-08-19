package com.softwarearchetypes.scoring.algebra.score.simplified;

import com.softwarearchetypes.scoring.algebra.score.Score;
import com.softwarearchetypes.scoring.ast.Expression;
import com.softwarearchetypes.scoring.context.MetricSource;

import java.util.ArrayList;
import java.util.List;

public final class ExpressionEvaluator {

    private ExpressionEvaluator() {
    }

    public static Score eval(Expression expr, MetricSource metricSource, ScoringAlgebra algebra) {
        if (expr instanceof Expression.And andExpr) {
            Score left = eval(andExpr.left(), metricSource, algebra);
            Score right = eval(andExpr.right(), metricSource, algebra);
            return algebra.and(left, right);

        } else if (expr instanceof Expression.Or orExpr) {
            Score left = eval(orExpr.left(), metricSource, algebra);
            Score right = eval(orExpr.right(), metricSource, algebra);
            return algebra.or(left, right);

        } else if (expr instanceof Expression.Not notExpr) {
            Score inner = eval(notExpr.inner(), metricSource, algebra);
            return algebra.not(inner);

        } else if (expr instanceof Expression.MetricComparison metricComparison) {
            return algebra.metricCmp(metricSource, metricComparison.metric(), metricComparison.op(), metricComparison.value());

        } else if (expr instanceof Expression.ConstantScore constantScore) {
            return algebra.constScore(constantScore.value());

        } else if (expr instanceof Expression.Sum sum) {
            List<Score> scores = new ArrayList<>();
            for (Expression child : sum.children()) {
                scores.add(eval(child, metricSource, algebra));
            }
            return algebra.sum(scores);

        } else if (expr instanceof Expression.IfThenElse ifThenElseExpr) {
            Score cond = eval(ifThenElseExpr.cond(), metricSource, algebra);
            Score thenScore = eval(ifThenElseExpr.thenBranch(), metricSource, algebra);
            Score elseScore = eval(ifThenElseExpr.elseBranch(), metricSource, algebra);
            return algebra.ifThenElse(cond, thenScore, elseScore);
        }

        throw new IllegalArgumentException("Unknown Expr type: " + expr.getClass());
    }
}
