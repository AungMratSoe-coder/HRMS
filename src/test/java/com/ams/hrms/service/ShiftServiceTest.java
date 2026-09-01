package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Shift;
import com.ams.hrms.repository.ShiftRepository;

/**
 * Shift definition rules (spec section 17) against a fake repository:
 * time/grace/break validation, uniqueness, update-existence check,
 * assignment-count guard before deactivation and no-op status changes.
 * (Assignment flows hit TransactionManager/DB and are covered by smoke tools.)
 */
class ShiftServiceTest extends ServiceTestSupport {

    private static final class FakeShiftRepository extends ShiftRepository {
        boolean codeTaken;
        boolean nameTaken;
        long openAssignments;
        Shift stored;
        final List<Shift> inserted = new java.util.ArrayList<>();
        final List<Shift> updated = new java.util.ArrayList<>();
        final List<String> statusChanges = new java.util.ArrayList<>();

        @Override
        public boolean codeExists(String code, Long excludeId) {
            return codeTaken;
        }

        @Override
        public boolean nameExists(String name, Long excludeId) {
            return nameTaken;
        }

        @Override
        public Optional<Shift> findById(long id) {
            return Optional.ofNullable(stored);
        }

        @Override
        public long insert(Shift shift) {
            inserted.add(shift);
            return 21;
        }

        @Override
        public void update(Shift shift) {
            updated.add(shift);
        }

        @Override
        public void setStatus(long id, String status) {
            statusChanges.add(id + "->" + status);
            if (stored != null && stored.getId() == id) {
                stored.setStatus(status);
            }
        }

        @Override
        public long openAssignmentCount(long shiftId) {
            return openAssignments;
        }
    }

    private final FakeShiftRepository repository = new FakeShiftRepository();
    private final RecordingAudit audit = new RecordingAudit();
    private final ShiftService service = new ShiftService(
            repository, null, null, audit, null);

    private static Shift newShift() {
        Shift shift = new Shift();
        shift.setCode("sh-night");
        shift.setName("Night Shift");
        shift.setStartTime(LocalTime.of(22, 0));
        shift.setEndTime(LocalTime.of(6, 0));
        return shift;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("identical start/end times and out-of-range grace/break are reported")
    void timeAndRangeValidation() {
        loginAs("SHIFT_MANAGE", "SHIFT_VIEW");

        Shift bad = newShift();
        bad.setStartTime(LocalTime.of(9, 0));
        bad.setEndTime(LocalTime.of(9, 0));
        bad.setGraceMinutes(241);
        bad.setBreakMinutes(-1);

        assertThatThrownBy(() -> service.save(bad))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getErrors())
                        .anyMatch(msg -> msg.contains("cannot be identical"))
                        .anyMatch(msg -> msg.contains("Grace period"))
                        .anyMatch(msg -> msg.contains("Break time")));

        assertThat(repository.inserted).isEmpty();
    }

    @Test
    @DisplayName("overnight shifts (end < start) are valid; only equality is rejected")
    void overnightShiftsAreAllowed() {
        loginAs("SHIFT_MANAGE", "SHIFT_VIEW");

        assertThatCode(() -> service.save(newShift())).doesNotThrowAnyException();
        assertThat(repository.inserted.getFirst().getCode()).isEqualTo("SH-NIGHT");
    }

    @Test
    @DisplayName("duplicate code or name blocks the save")
    void uniquenessChecks() {
        loginAs("SHIFT_MANAGE", "SHIFT_VIEW");
        repository.codeTaken = true;

        assertThatThrownBy(() -> service.save(newShift()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("'SH-NIGHT'");

        repository.codeTaken = false;
        repository.nameTaken = true;
        assertThatThrownBy(() -> service.save(newShift()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Night Shift");
    }

    // ------------------------------------------------------------------
    // Create / update behaviour
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create audits; update of a missing shift fails cleanly")
    void createAndUpdatePaths() {
        loginAs("SHIFT_MANAGE");

        long id = service.save(newShift());
        assertThat(id).isEqualTo(21);
        assertThat(audit.last().action()).isEqualTo("CREATE");
        assertThat(audit.last().module()).isEqualTo("SHIFT");

        Shift missing = newShift();
        missing.setId(99L);
        repository.stored = null;
        assertThatThrownBy(() -> service.save(missing))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getUserMessage()).contains("no longer exists"));

        Shift existing = newShift();
        existing.setId(21L);
        repository.stored = existing;
        service.save(existing);
        assertThat(repository.updated).hasSize(1);
        assertThat(audit.last().action()).isEqualTo("UPDATE");
    }

    // ------------------------------------------------------------------
    // Status toggle guards
    // ------------------------------------------------------------------

    @Test
    @DisplayName("deactivation blocked while employees are assigned")
    void deactivateBlockedByAssignments() {
        loginAs("SHIFT_MANAGE");
        Shift existing = newShift();
        existing.setId(4L);
        existing.setStatus("ACTIVE");
        repository.stored = existing;
        repository.openAssignments = 2;

        assertThatThrownBy(() -> service.setStatus(4L, "INACTIVE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2")
                .hasMessageContaining("assigned");

        assertThat(repository.statusChanges).isEmpty();
    }

    @Test
    @DisplayName("status change writes only when the status actually differs")
    void statusChangeWritesOnlyOnDifference() {
        loginAs("SHIFT_MANAGE");
        Shift existing = newShift();
        existing.setId(4L);
        existing.setStatus("ACTIVE");
        repository.stored = existing;

        service.setStatus(4L, "INACTIVE");
        assertThat(repository.statusChanges).containsExactly("4->INACTIVE");
        assertThat(audit.last().action()).isEqualTo("STATUS_CHANGE");

        service.setStatus(4L, "INACTIVE");
        assertThat(repository.statusChanges).hasSize(1);
    }
}
