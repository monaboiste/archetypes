package com.softwarearchetypes.scoring.algebra.explained;

import com.softwarearchetypes.scoring.algebra.Monoid;

import java.util.List;

// The second monoid is the proof that the engine is result-type agnostic: same rules, same window,
// but the total carries labelled contributions instead of a bare number. ExplainedScore.plus already
// merges them.
public class ExplainedScoreMonoid implements Monoid<ExplainedScore> {

    @Override
    public ExplainedScore zero() {
        return new ExplainedScore(0, List.of());
    }

    @Override
    public ExplainedScore combine(ExplainedScore left, ExplainedScore right) {
        return left.plus(right);
    }
}
