package com.softwarearchetypes.scoring.algebra.score;

import com.softwarearchetypes.scoring.algebra.Monoid;

// The arithmetic already lives on the value object; this only tells the engine where to find it.
public class ScoreMonoid implements Monoid<Score> {

    @Override
    public Score zero() {
        return Score.ZERO;
    }

    @Override
    public Score combine(Score left, Score right) {
        return left.plus(right);
    }
}
