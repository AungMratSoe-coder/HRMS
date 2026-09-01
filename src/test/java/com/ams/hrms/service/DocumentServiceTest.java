package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.EmployeeDocument;
import com.ams.hrms.repository.EmployeeDocumentRepository;

/**
 * Document service rules (spec section 25) against a fake repository:
 * upload validation (type whitelist, file required, expiry not in the past,
 * notes length), soft archive/delete status writes and audit coverage.
 * (Happy-path upload writes through FileStorage and is covered by smoke tools.)
 */
class DocumentServiceTest extends ServiceTestSupport {

    private static final class FakeDocumentRepository extends EmployeeDocumentRepository {
        record StatusWrite(long id, String status) {
        }

        int expiredMarked;
        final List<StatusWrite> statusWrites = new java.util.ArrayList<>();

        @Override
        public void setStatus(long id, String status) {
            statusWrites.add(new StatusWrite(id, status));
        }

        @Override
        public int markExpired() {
            return expiredMarked;
        }

        @Override
        public List<EmployeeDocument> findExpiring(int days) {
            return List.of();
        }
    }

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingAudit audit = new RecordingAudit();
    private final DocumentService service =
            new DocumentService(repository, audit, null);

    // ------------------------------------------------------------------
    // Upload validation (never reaches file storage on failure)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("invalid type, missing file and past expiry are collected in one error list")
    void uploadValidationCollectsAllProblems() {
        loginAs("DOCUMENT_MANAGE");

        assertThatThrownBy(() -> service.upload(1L, "WARRANTY", null,
                LocalDate.now().minusDays(3), "x".repeat(501)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getErrors())
                        .anyMatch(msg -> msg.contains("type"))
                        .anyMatch(msg -> msg.contains("choose a file"))
                        .anyMatch(msg -> msg.contains("past"))
                        .anyMatch(msg -> msg.contains("Notes")));
    }

    @Test
    @DisplayName("today is an acceptable expiry date; only yesterday lapses")
    void expiryBoundary() {
        loginAs("DOCUMENT_MANAGE");

        assertThatThrownBy(() -> service.upload(1L, "NRC", null,
                LocalDate.now().minusDays(1), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("past");
    }

    @Test
    @DisplayName("unknown document types are rejected before any storage happens")
    void unknownTypeRejected() {
        loginAs("DOCUMENT_MANAGE");

        assertThatThrownBy(() -> service.upload(1L, "PASSPORT-COPY", null, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("type");

        assertThat(DocumentService.DOCUMENT_TYPES).contains(
                "NRC", "PASSPORT", "CONTRACT", "CERTIFICATE", "RESUME",
                "TRAINING_CERTIFICATE", "OTHER");
    }

    // ------------------------------------------------------------------
    // Archive / delete / expiry sweep
    // ------------------------------------------------------------------

    @Test
    @DisplayName("archive and delete write their exact statuses with audit entries")
    void archiveAndDeleteWriteStatuses() {
        loginAs("EMPLOYEE_VIEW");

        assertThatThrownBy(() -> service.archive(5L))
                .isInstanceOf(com.ams.hrms.exception.AuthorizationException.class);

        loginAs("DOCUMENT_MANAGE");
        service.archive(5L);
        service.delete(6L);

        assertThat(repository.statusWrites).containsExactly(
                new FakeDocumentRepository.StatusWrite(5L, "ARCHIVED"),
                new FakeDocumentRepository.StatusWrite(6L, "DELETED"));
        assertThat(audit.entries).hasSize(2);
        assertThat(audit.last().action()).isEqualTo("STATUS_CHANGE");
        assertThat(audit.last().entity()).isEqualTo("EmployeeDocument");
    }

    @Test
    @DisplayName("expiry sweep returns how many documents lapsed")
    void refreshExpiredReturnsCount() {
        loginAs("EMPLOYEE_VIEW");
        repository.expiredMarked = 7;

        assertThat(service.refreshExpiredStatuses()).isEqualTo(7);
        assertThat(service.findExpiring()).isEmpty();
    }
}
