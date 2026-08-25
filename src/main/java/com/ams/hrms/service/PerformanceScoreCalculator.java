package com.ams.hrms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Weighted overall review score (spec sections 22 and 55): the mean of item
 * scores weighted by each criterion's weight, normalized over the weights
 * actually scored, clamped to the 1..5 envelope and rounded half-up to two
 * decimals. Pure logic - unit-testable without UI or database.
 */
public final class PerformanceScoreCalculator {

    /** One scored contribution: 1..5 rating carrying its criterion weight. */
    public record ScoredWeight(BigDecimal score, BigDecimal weight) {
    }

    private static final BigDecimal MIN_SCORE = BigDecimal.ONE;
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(5);

    private PerformanceScoreCalculator() {
    }

    /**
     * @param scored scored items with their criterion weights
     * @return the weighted average, or null when nothing is scored
     */
    public static BigDecimal weightedOverall(List<ScoredWeight> scored) {
        if (scored == null || scored.isEmpty()) {
            return null;
        }
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        for (ScoredWeight entry : scored) {
            if (entry.score() == null || entry.weight() == null) {
                continue;
            }
            weightedSum = weightedSum.add(entry.score().multiply(entry.weight()));
            weightSum = weightSum.add(entry.weight());
        }
        if (weightSum.signum() == 0) {
            return null;
        }
        BigDecimal average = weightedSum.divide(weightSum, 2, RoundingMode.HALF_UP);
        return clamp(average);
    }

    /** Keeps legacy data inside the 1..5 envelope enforced by the schema. */
    private static BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(MIN_SCORE) < 0) {
            return MIN_SCORE;
        }
        if (value.compareTo(MAX_SCORE) > 0) {
            return MAX_SCORE;
        }
        return value;
    }
}
