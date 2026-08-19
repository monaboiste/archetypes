package com.softwarearchetypes.scoring.algebra;

import com.softwarearchetypes.scoring.ast.Expression;
import com.softwarearchetypes.scoring.ast.ExpressionVisitor;
import com.softwarearchetypes.scoring.context.MetricSource;

import java.util.ArrayList;
import java.util.List;

// PATTERN: Visitor + Interpreter. This class owns the traversal and the double dispatch; the
// Algebra owns the semantics. The same expression tree therefore yields points, a fuzzy degree or an
// explanation, and - since the only data it holds is a MetricSource - it knows nothing about the
// domain the metrics come from.
public class AlgebraicVisitor<R> implements ExpressionVisitor<R> {

    private final MetricSource metrics;
    private final Algebra<R> algebra;

    public AlgebraicVisitor(MetricSource metrics, Algebra<R> algebra) {
        this.metrics = metrics;
        this.algebra = algebra;
    }

    @Override
    public R visit(Expression.And expr) {
        R left = expr.left().accept(this);
        R right = expr.right().accept(this);
        return algebra.and(left, right);
    }

    @Override
    public R visit(Expression.Or expr) {
        R left = expr.left().accept(this);
        R right = expr.right().accept(this);
        return algebra.or(left, right);
    }

    @Override
    public R visit(Expression.Not expr) {
        R inner = expr.inner().accept(this);
        return algebra.not(inner);
    }

    @Override
    public R visit(Expression.MetricComparison expr) {
        return algebra.metricCmp(metrics, expr.metric(), expr.op(), expr.value());
    }

    @Override
    public R visit(Expression.ConstantScore expr) {
        return algebra.constScore(expr.value());
    }

    @Override
    public R visit(Expression.Sum expr) {
        List<R> list = new ArrayList<>();
        for (Expression child : expr.children()) {
            list.add(child.accept(this));
        }
        return algebra.sum(list);
    }

    @Override
    public R visit(Expression.IfThenElse expr) {
        R cond = expr.cond().accept(this);
        R thenValue = expr.thenBranch().accept(this);
        R elseValue = expr.elseBranch().accept(this);
        return algebra.ifThenElse(cond, thenValue, elseValue);
    }

    @Override
    public R visit(Expression.Labeled expr) {
        R inner = expr.inner().accept(this);
        return algebra.label(expr.label(), inner);
    }
}
