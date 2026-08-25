package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Weighted overall score math (spec sections 22 and 55): pure logic, verified
 * without UI or database.
 */
class PerformanceScoreCalculatorTest {

    private static PerformanceScoreCalculator.ScoredWeight scored(String score, String weight) {
        return new PerformanceScoreCalculator.ScoredWeight(
                new BigDecimal(score), new BigDecimal(weight));
    }

    @Test
    void emptyOrNullInputYieldsNull() {
        assertThat(PerformanceScoreCalculator.weightedOverall(null)).isNull();
        assertThat(PerformanceScoreCalculator.weightedOverall(List.of())).isNull();
    }

    @Test
    void equalWeightsProducePlainAverage() {
        BigDecimal result = PerformanceScoreCalculator.weightedOverall(List.of(
                scored("5", "20"), scored("3", "20")));
        assertThat(result).isEqualByComparingTo("4.00");
    }

    @Test
    void heavierWeightsDominate() {
        // 5 at weight 80 + 1 at weight 20 -> 0.8*5 + 0.2*1 = 4.2
        BigDecimal result = PerformanceScoreCalculator.weightedOverall(List.of(
                scored("5", "80"), scored("1", "20")));
        assertThat(result).isEqualByComparingTo("4.20");
    }

    @Test
    void weightsAreNormalizedOverScoredItemsOnly() {
        // Only two of three criteria scored: weights renormalize to 40/60.
        BigDecimal result = PerformanceScoreCalculator.weightedOverall(List.of(
                scored("2", "40"), scored("5", "60")));
        // (2*40 + 5*60) / 100 = 3.80
        assertThat(result).isEqualByComparingTo("3.80");
    }

    @Test
    void halfUpRoundingToTwoDecimals() {
        BigDecimal result = PerformanceScoreCalculator.weightedOverall(List.of(
                scored("3.5", "10"), scored("4", "90")));
        // (35 + 360) / 100 = 3.95
        assertThat(result).isEqualByComparingTo("3.95");

        BigDecimal repeating = PerformanceScoreCalculator.weightedOverall(List.of(
                scored("1", "30"), scored("2", "30"), scored("2", "30")));
        // 5/9 * ... -> 1.6666... rounds to 1.67
        assertThat(repeating).isEqualByComparingTo("1.67");
    }

    @Test
    void resultIsClampedToSchemaEnvelope() {
        BigDecimal low = PerformanceScoreCalculator.weightedOverall(List.of(
                scored("0.5", "50"), scored("1", "50")));
        assertThat(low).isEqualByComparingTo("1.00");

        BigDecimal high = PerformanceScoreCalculator.weightedOverall(List.of(
                scored("9", "50"), scored("5", "50")));
        assertThat(high).isEqualByComparingTo("5.00");
    }

    @Test
    void zeroTotalWeightYieldsNull() {
        assertThat(PerformanceScoreCalculator.weightedOverall(List.of(
                scored("4", "0"), scored("5", "0")))).isNull();
    }

    @Test
    void nullEntriesAreSkippedSafely() {
        BigDecimal result = PerformanceScoreCalculator.weightedOverall(List.of(
                new PerformanceScoreCalculator.ScoredWeight(null, new BigDecimal("50")),
                scored("4", "50")));
        assertThat(result).isEqualByComparingTo("4.00");
    }
}
