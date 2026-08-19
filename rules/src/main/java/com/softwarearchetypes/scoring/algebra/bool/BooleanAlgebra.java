package com.softwarearchetypes.scoring.algebra.bool;

import com.softwarearchetypes.scoring.algebra.Algebra;
import com.softwarearchetypes.scoring.ast.CmpOp;
import com.softwarearchetypes.scoring.ast.Metric;
import com.softwarearchetypes.scoring.context.MetricSource;

import java.util.List;

// PATTERN: a fourth algebra over the same AST - the Boolean one, which "reproduces ordinary
// specifications". It obeys the same laws as the And / Or / Not specification nodes built in L03
// (rules.core.predicates); only the carrier differs, an expression tree instead of a composed
// Predicate. The filter phase now has its own result type instead of borrowing Score's: forcing a
// scoring algebra to act as a filter (the old "score > 0") misreads negative points as false.
public class BooleanAlgebra implements Algebra<Boolean> {

    @Override
    public Boolean and(Boolean left, Boolean right) {
        return left && right;
    }

    @Override
    public Boolean or(Boolean left, Boolean right) {
        return left || right;
    }

    @Override
    public Boolean not(Boolean inner) {
        return !inner;
    }

    @Override
    public Boolean metricCmp(MetricSource source, Metric metric, CmpOp op, double value) {
        return op.compare(source.metric(metric), value); // no 0/1 detour, the comparison IS the answer
    }

    @Override
    public Boolean constScore(int value) {
        return value != 0; // same mapping FuzzyAlgebra uses: zero is the only falsy constant
    }

    @Override
    public Boolean sum(List<Boolean> list) {
        // Addition in the Boolean semiring is disjunction, so a filter written as a Sum of conditions
        // reads "any of these". Empty Sum is false, the identity of OR.
        return list.stream().anyMatch(Boolean::booleanValue);
    }

    @Override
    public Boolean ifThenElse(Boolean cond, Boolean thenV, Boolean elseV) {
        return cond ? thenV : elseV;
    }
}
