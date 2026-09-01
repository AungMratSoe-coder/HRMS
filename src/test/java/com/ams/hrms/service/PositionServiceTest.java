package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Position;
import com.ams.hrms.repository.PositionRepository;

/**
 * Position service rules (spec sections 13 and 46) against a fake
 * repository: salary envelope sanity, per-department name uniqueness,
 * RBAC split, deactivation guard (rule 3) and audit coverage.
 */
class PositionServiceTest extends ServiceTestSupport {

    private static final class FakePositionRepository extends PositionRepository {
        boolean codeTaken;
        boolean nameTakenInDepartment;
        long activeEmployees;
        Optional<Position> byId = Optional.empty();
        final List<Position> inserted = new java.util.ArrayList<>();
        final List<String> statusChanges = new java.util.ArrayList<>();

        @Override
        public boolean codeExists(String code, Long excludeId) {
            return codeTaken;
        }

        @Override
        public boolean nameExistsInDepartment(String name, long departmentId, Long excludeId) {
            return nameTakenInDepartment;
        }

        @Override
        public Optional<Position> findById(long id) {
            return byId;
        }

        @Override
        public long insert(Position position) {
            inserted.add(position);
            return 55;
        }

        @Override
        public void update(Position position) {
            // captured via inserted/update-free assertions; nothing to do
        }

        @Override
        public void setStatus(long id, String status) {
            statusChanges.add(id + "->" + status);
        }

        @Override
        public long activeEmployeeCount(long positionId) {
            return activeEmployees;
        }
    }

    private final FakePositionRepository repository = new FakePositionRepository();
    private final RecordingAudit audit = new RecordingAudit();
    private final PositionService service = new PositionService(repository, audit);

    private static Position newPosition() {
        Position position = new Position();
        position.setCode("dev-01");
        position.setName("Developer");
        position.setDepartmentId(5L);
        position.setMinSalary(new BigDecimal("1000"));
        position.setMaxSalary(new BigDecimal("2000"));
        return position;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("missing department, inverted salaries and negatives are all reported")
    void validationCollectsAllProblems() {
        loginAs("POSITION_CREATE", "POSITION_VIEW");

        Position bad = new Position();
        bad.setCode("DEV 1");
        bad.setName("Developer");
        bad.setMinSalary(new BigDecimal("3000"));
        bad.setMaxSalary(new BigDecimal("-1"));

        assertThatThrownBy(() -> service.save(bad))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getErrors())
                        .anyMatch(msg -> msg.contains("Department is required"))
                        .anyMatch(msg -> msg.contains("exceed maximum"))
                        .anyMatch(msg -> msg.contains("cannot be negative")));

        assertThat(repository.inserted).isEmpty();
    }

    @Test
    @DisplayName("duplicate code or duplicate name inside the department blocks the save")
    void uniquenessChecks() {
        loginAs("POSITION_CREATE", "POSITION_VIEW");
        repository.codeTaken = true;

        assertThatThrownBy(() -> service.save(newPosition()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("'DEV-01'");

        repository.codeTaken = false;
        repository.nameTakenInDepartment = true;
        assertThatThrownBy(() -> service.save(newPosition()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists in this department");
    }

    // ------------------------------------------------------------------
    // Create / update behaviour
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create needs POSITION_CREATE, forces ACTIVE, audits with normalized values")
    void createNormalizesAndAudits() {
        assertThatThrownBy(() -> service.save(newPosition()))
                .isInstanceOf(com.ams.hrms.exception.AuthenticationException.class);

        loginAs("POSITION_CREATE", "POSITION_VIEW");
        long id = service.save(newPosition());

        assertThat(id).isEqualTo(55);
        Position saved = repository.inserted.getFirst();
        assertThat(saved.getCode()).isEqualTo("DEV-01");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(audit.last().action()).isEqualTo("CREATE");
        assertThat(audit.last().module()).isEqualTo("ORG");
    }

    @Test
    @DisplayName("update of an existing record requires POSITION_UPDATE")
    void updateRequiresUpdatePermission() {
        Position existing = newPosition();
        existing.setId(9L);

        loginAs("POSITION_VIEW");
        assertThatThrownBy(() -> service.save(existing))
                .isInstanceOf(AuthorizationException.class);

        loginAs("POSITION_UPDATE", "POSITION_VIEW");
        assertThatCode(() -> service.save(existing)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Status toggle guards (spec rule 3)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("deactivation blocked while employees hold the position")
    void deactivateBlockedByEmployees() {
        loginAs("POSITION_UPDATE", "POSITION_VIEW");
        Position existing = newPosition();
        existing.setId(2L);
        existing.setStatus("ACTIVE");
        existing.setCode("DEV-01");
        repository.byId = Optional.of(existing);
        repository.activeEmployees = 3;

        assertThatThrownBy(() -> service.setStatus(2L, "INACTIVE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("employee");

        assertThat(repository.statusChanges).isEmpty();
    }

    @Test
    @DisplayName("no-op when the status already matches; unknown id fails cleanly")
    void noOpAndUnknownId() {
        loginAs("POSITION_UPDATE", "POSITION_VIEW");
        Position existing = newPosition();
        existing.setId(2L);
        existing.setStatus("INACTIVE");
        repository.byId = Optional.of(existing);

        service.setStatus(2L, "INACTIVE");
        assertThat(repository.statusChanges).isEmpty();

        repository.byId = Optional.empty();
        assertThatThrownBy(() -> service.setStatus(77L, "INACTIVE"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getUserMessage()).contains("no longer exists"));
    }
}
