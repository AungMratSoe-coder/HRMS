package com.ams.hrms.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ams.hrms.repository.AuditRepository.Filter;
import com.ams.hrms.repository.AuditRepository.SqlFragment;

/**
 * Audit viewer filter rules (spec sections 28 and 55): the pure WHERE-clause
 * builder used by {@link AuditRepository} - deterministic SQL text plus bound
 * parameters, verified without any database.
 */
class AuditFilterTest {

    // ------------------------------------------------------------------
    // Normalization
    // ------------------------------------------------------------------

    @Test
    void blanksNormalizeToEmpty() {
        Filter filter = new Filter("  ", "  APPROVE ", null, null, null, null);
        assertThat(filter.keyword()).isEmpty();
        assertThat(filter.action()).isEqualTo("APPROVE");
        assertThat(filter.module()).isEmpty();
    }

    @Test
    void keywordIsTrimmedNotLowered() {
        Filter filter = new Filter("  Payroll  ", "", "", null, null, null);
        assertThat(filter.keyword()).isEqualTo("Payroll");
    }

    // ------------------------------------------------------------------
    // WHERE building
    // ------------------------------------------------------------------

    @Test
    void emptyFilterMatchesEverythingWithNoParams() {
        SqlFragment fragment = AuditRepository.buildWhere(Filter.empty());
        assertThat(fragment.sql()).isEqualTo("1 = 1");
        assertThat(fragment.params()).isEmpty();
    }

    @Test
    void keywordSearchesFourColumnsWithWildcards() {
        SqlFragment fragment = AuditRepository.buildWhere(new Filter(
                "payroll", "", "", null, null, null));

        assertThat(fragment.sql())
                .contains("a.description LIKE ?")
                .contains("a.entity LIKE ?")
                .contains("a.action LIKE ?")
                .contains("u.username LIKE ?");
        assertThat(fragment.params()).hasSize(4);
        assertThat(fragment.params()).containsOnly("%payroll%");
    }

    @Test
    void exactActionAndModuleUseEquality() {
        SqlFragment fragment = AuditRepository.buildWhere(new Filter(
                "", "LOGIN", "SECURITY", null, null, null));

        assertThat(fragment.sql()).isEqualTo("a.action = ? AND a.module = ?");
        assertThat(fragment.params()).containsExactly("LOGIN", "SECURITY");
    }

    @Test
    void userFilterBindsUserId() {
        SqlFragment fragment = AuditRepository.buildWhere(new Filter(
                "", "", "", 42L, null, null));

        assertThat(fragment.sql()).isEqualTo("a.user_id = ?");
        assertThat(fragment.params()).containsExactly(42L);
    }

    @Test
    void dateRangeIsInclusiveOnBothEnds() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 23);

        SqlFragment fragment = AuditRepository.buildWhere(new Filter(
                "", "", "", null, from, to));

        assertThat(fragment.sql()).isEqualTo("a.created_at >= ? AND a.created_at < ?");
        assertThat(fragment.params()).containsExactly(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
    }

    @Test
    void openEndedRangesBindOnlyTheGivenSide() {
        SqlFragment fromOnly = AuditRepository.buildWhere(new Filter(
                "", "", "", null, LocalDate.of(2026, 8, 1), null));
        assertThat(fromOnly.sql()).isEqualTo("a.created_at >= ?");
        assertThat(fromOnly.params()).hasSize(1);

        SqlFragment toOnly = AuditRepository.buildWhere(new Filter(
                "", "", "", null, null, LocalDate.of(2026, 8, 23)));
        assertThat(toOnly.sql()).isEqualTo("a.created_at < ?");
        assertThat(toOnly.params()).hasSize(1);
    }

    @Test
    void combinedFilterAccumulatesAllConditionsInOrder() {
        Filter filter = new Filter(
                "leave", "APPROVE", "LEAVE", 7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        SqlFragment fragment = AuditRepository.buildWhere(filter);

        List<Object> params = fragment.params();
        assertThat(fragment.sql()).isEqualTo(String.join(" AND ",
                "(a.description LIKE ? OR a.entity LIKE ? OR a.action LIKE ? OR u.username LIKE ?)",
                "a.action = ?",
                "a.module = ?",
                "a.user_id = ?",
                "a.created_at >= ?",
                "a.created_at < ?"));
        assertThat(params).hasSize(9);
        assertThat(params.subList(0, 4)).allMatch("%leave%"::equals);
        assertThat(params.get(7)).isEqualTo(LocalDate.of(2026, 8, 1).atStartOfDay());
        assertThat(params.get(8)).isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay());
    }
}
