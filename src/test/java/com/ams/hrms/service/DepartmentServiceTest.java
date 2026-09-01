package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Department;
import com.ams.hrms.repository.DepartmentRepository;

/**
 * Department service rules (spec sections 12 and 46) verified against a
 * fake repository: validation wording, uniqueness, RBAC split between
 * create/update, deactivation referential guards (rule 2), no-op status
 * changes and audit coverage.
 */
class DepartmentServiceTest extends ServiceTestSupport {

    private static final class FakeDepartmentRepository extends DepartmentRepository {
        boolean codeTaken;
        boolean nameTaken;
        long activeEmployees;
        long activePositions;
        Optional<Department> byId = Optional.empty();
        long nextId = 100;
        final List<Department> inserted = new ArrayList<>();
        final List<Department> updated = new ArrayList<>();
        final List<String> statusChanges = new ArrayList<>();

        @Override
        public boolean codeExists(String code, Long excludeId) {
            return codeTaken;
        }

        @Override
        public boolean nameExists(String name, Long excludeId) {
            return nameTaken;
        }

        @Override
        public Optional<Department> findById(long id) {
            return byId;
        }

        @Override
        public long insert(Department department) {
            inserted.add(department);
            return nextId;
        }

        @Override
        public void update(Department department) {
            updated.add(department);
        }

        @Override
        public void setStatus(long id, String status) {
            statusChanges.add(id + "->" + status);
        }

        @Override
        public long activeEmployeeCount(long departmentId) {
            return activeEmployees;
        }

        @Override
        public long activePositionCount(long departmentId) {
            return activePositions;
        }
    }

    private final FakeDepartmentRepository repository = new FakeDepartmentRepository();
    private final RecordingAudit audit = new RecordingAudit();
    private final DepartmentService service = new DepartmentService(repository, audit);

    private static Department newDepartment() {
        Department department = new Department();
        department.setCode("hr");
        department.setName("  Human Resources  ");
        return department;
    }

    // ------------------------------------------------------------------
    // RBAC
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create needs DEPARTMENT_CREATE; update needs DEPARTMENT_UPDATE")
    void saveEnforcesPermissionSplit() {
        loginAs("DEPARTMENT_UPDATE");
        assertThatThrownBy(() -> service.save(newDepartment()))
                .isInstanceOf(AuthorizationException.class);

        Department existing = newDepartment();
        existing.setId(5L);
        loginAs("DEPARTMENT_CREATE");
        assertThatThrownBy(() -> service.save(existing))
                .isInstanceOf(AuthorizationException.class);

        loginAs("DEPARTMENT_CREATE", "DEPARTMENT_UPDATE", "DEPARTMENT_VIEW");
        assertThatCode(() -> service.save(newDepartment())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("findAll needs DEPARTMENT_VIEW")
    void findAllRequiresViewPermission() {
        assertThatThrownBy(() -> service.findAll(null))
                .isInstanceOf(com.ams.hrms.exception.AuthenticationException.class);
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("blank code/name and bad formats are collected in one error list")
    void validationCollectsAllProblems() {
        loginAs("DEPARTMENT_CREATE", "DEPARTMENT_VIEW");

        Department bad = new Department();
        bad.setCode("IT DEV!");
        bad.setName(" ");
        bad.setDescription("x".repeat(501));

        assertThatThrownBy(() -> service.save(bad))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getErrors())
                        .anyMatch(msg -> msg.contains("code"))
                        .anyMatch(msg -> msg.contains("name"))
                        .anyMatch(msg -> msg.contains("Description")));

        assertThat(repository.inserted).isEmpty();
        assertThat(audit.entries).isEmpty();
    }

    @Test
    @DisplayName("over-long name is rejected")
    void nameLengthIsCapped() {
        loginAs("DEPARTMENT_CREATE", "DEPARTMENT_VIEW");

        Department department = newDepartment();
        department.setName("N".repeat(121));

        assertThatThrownBy(() -> service.save(department))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("120");
    }

    @Test
    @DisplayName("duplicate code or name blocks the save after normalization")
    void uniquenessIsCheckedOnNormalizedValues() {
        loginAs("DEPARTMENT_CREATE", "DEPARTMENT_VIEW");
        repository.codeTaken = true;

        assertThatThrownBy(() -> service.save(newDepartment()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("'HR'");

        repository.codeTaken = false;
        repository.nameTaken = true;
        assertThatThrownBy(() -> service.save(newDepartment()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Human Resources");
    }

    // ------------------------------------------------------------------
    // Create / update behaviour
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create normalizes values, forces ACTIVE, audits and returns the id")
    void createNormalizesAndAudits() {
        loginAs("DEPARTMENT_CREATE", "DEPARTMENT_VIEW");

        long id = service.save(newDepartment());

        assertThat(id).isEqualTo(100);
        Department saved = repository.inserted.getFirst();
        assertThat(saved.getCode()).isEqualTo("HR");
        assertThat(saved.getName()).isEqualTo("Human Resources");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(audit.last().action()).isEqualTo("CREATE");
        assertThat(audit.last().entity()).isEqualTo("Department");
        assertThat(audit.last().description()).contains("HR", "Human Resources");
    }

    @Test
    @DisplayName("update keeps the record's own status untouched")
    void updateDoesNotForceStatus() {
        loginAs("DEPARTMENT_UPDATE", "DEPARTMENT_VIEW");
        Department existing = newDepartment();
        existing.setId(9L);
        existing.setStatus("ACTIVE");

        long id = service.save(existing);

        assertThat(id).isEqualTo(9L);
        assertThat(repository.updated).hasSize(1);
        assertThat(repository.inserted).isEmpty();
        assertThat(audit.last().action()).isEqualTo("UPDATE");
    }

    // ------------------------------------------------------------------
    // Status toggle guards (spec rule 2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("deactivation is blocked while active employees remain")
    void deactivateBlockedByEmployees() {
        loginAs("DEPARTMENT_UPDATE", "DEPARTMENT_VIEW");
        Department existing = newDepartment();
        existing.setId(3L);
        existing.setStatus("ACTIVE");
        existing.setCode("HR");
        repository.byId = Optional.of(existing);
        repository.activeEmployees = 4;

        assertThatThrownBy(() -> service.setStatus(3L, "INACTIVE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("4")
                .hasMessageContaining("employee");

        assertThat(repository.statusChanges).isEmpty();
    }

    @Test
    @DisplayName("deactivation is blocked while active positions remain")
    void deactivateBlockedByPositions() {
        loginAs("DEPARTMENT_UPDATE", "DEPARTMENT_VIEW");
        Department existing = newDepartment();
        existing.setId(3L);
        existing.setStatus("ACTIVE");
        existing.setCode("HR");
        repository.byId = Optional.of(existing);
        repository.activePositions = 2;

        assertThatThrownBy(() -> service.setStatus(3L, "INACTIVE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("position");

        assertThat(repository.statusChanges).isEmpty();
    }

    @Test
    @DisplayName("activation skips the referential guards entirely")
    void activationSkipsGuards() {
        loginAs("DEPARTMENT_UPDATE", "DEPARTMENT_VIEW");
        Department existing = newDepartment();
        existing.setId(3L);
        existing.setStatus("INACTIVE");
        repository.byId = Optional.of(existing);
        repository.activeEmployees = 6;
        repository.activePositions = 6;

        service.setStatus(3L, "ACTIVE");

        assertThat(repository.statusChanges).containsExactly("3->ACTIVE");
        assertThat(audit.last().action()).isEqualTo("STATUS_CHANGE");
    }

    @Test
    @DisplayName("same-status calls are a silent no-op without DB writes")
    void sameStatusIsNoOp() {
        loginAs("DEPARTMENT_UPDATE", "DEPARTMENT_VIEW");
        Department existing = newDepartment();
        existing.setId(3L);
        existing.setStatus("ACTIVE");
        repository.byId = Optional.of(existing);

        service.setStatus(3L, "ACTIVE");

        assertThat(repository.statusChanges).isEmpty();
        assertThat(audit.entries).isEmpty();
    }

    @Test
    @DisplayName("unknown departments fail with a friendly message")
    void unknownDepartmentFails() {
        loginAs("DEPARTMENT_UPDATE", "DEPARTMENT_VIEW");
        repository.byId = Optional.empty();

        assertThatThrownBy(() -> service.setStatus(42L, "INACTIVE"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getUserMessage()).contains("no longer exists"));
    }
}
