package ug.go.caa.recruitment.feature.recruitment.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AssessmentRepository {

    private final JdbcClient jdbc;

    public AssessmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<AssessmentData> findAll() {
        return jdbc.sql("""
                SELECT a.*, ap.candidate_name, ap.title AS job_title, ap.dept
                FROM assessments a JOIN applications ap ON ap.id = a.application_id
                ORDER BY a.scheduled_at DESC NULLS LAST
                """)
                .query((row, number) -> map(row, true)).list();
    }

    public List<AssessmentData> findByApplication(long applicationId) {
        return jdbc.sql("""
                SELECT a.* FROM assessments a
                WHERE application_id = :applicationId ORDER BY type
                """)
                .param("applicationId", applicationId)
                .query((row, number) -> map(row, false)).list();
    }

    public Optional<AssessmentData> find(long applicationId, String type) {
        return jdbc.sql("""
                SELECT a.* FROM assessments a
                WHERE application_id = :applicationId AND type = :type
                """)
                .param("applicationId", applicationId).param("type", type)
                .query((row, number) -> map(row, false)).optional();
    }

    public Optional<ApplicationSummary> application(long id) {
        return jdbc.sql("""
                SELECT id, candidate_name, title, status FROM applications WHERE id = :id
                """)
                .param("id", id)
                .query((row, number) -> new ApplicationSummary(
                        row.getLong("id"), row.getString("candidate_name"),
                        row.getString("title"), row.getString("status")))
                .optional();
    }

    public AssessmentData save(AssessmentWrite assessment) {
        jdbc.sql("""
                INSERT INTO assessments (
                    application_id, type, scheduled_at, venue, scheduled_by,
                    score, passed, notes, recorded_by
                ) VALUES (
                    :applicationId, :type, :scheduledAt, :venue, :scheduledBy,
                    :score, :passed, :notes, :recordedBy
                )
                ON CONFLICT (application_id, type) DO UPDATE SET
                    scheduled_at = excluded.scheduled_at,
                    venue = excluded.venue,
                    scheduled_by = excluded.scheduled_by,
                    score = excluded.score,
                    passed = excluded.passed,
                    notes = excluded.notes,
                    recorded_by = excluded.recorded_by,
                    updated_at = now()
                """)
                .param("applicationId", assessment.applicationId())
                .param("type", assessment.type())
                .param("scheduledAt", assessment.scheduledAt())
                .param("venue", assessment.venue())
                .param("scheduledBy", assessment.scheduledBy())
                .param("score", assessment.score())
                .param("passed", assessment.passed())
                .param("notes", assessment.notes())
                .param("recordedBy", assessment.recordedBy())
                .update();
        return find(assessment.applicationId(), assessment.type()).orElseThrow();
    }

    public void updateApplicationStatus(long applicationId, String status) {
        jdbc.sql("""
                UPDATE applications SET status = :status, updated_at = now() WHERE id = :id
                """)
                .param("status", status).param("id", applicationId).update();
    }

    public boolean allScheduledRecorded(long applicationId) {
        return jdbc.sql("""
                SELECT count(*) > 0 AND bool_and(scheduled_at IS NULL OR passed IS NOT NULL)
                FROM assessments WHERE application_id = :applicationId
                """)
                .param("applicationId", applicationId).query(Boolean.class).single();
    }

    private AssessmentData map(ResultSet row, boolean report) throws SQLException {
        return new AssessmentData(
                row.getLong("id"), row.getLong("application_id"), row.getString("type"),
                row.getObject("scheduled_at", OffsetDateTime.class), row.getString("venue"),
                row.getBigDecimal("score"), nullableBoolean(row, "passed"), row.getString("notes"),
                report ? row.getString("candidate_name") : null,
                report ? row.getString("job_title") : null,
                report ? row.getString("dept") : null,
                nullableLong(row, "scheduled_by"), nullableLong(row, "recorded_by"));
    }

    private static Boolean nullableBoolean(ResultSet row, String column) throws SQLException {
        boolean value = row.getBoolean(column);
        return row.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    public record ApplicationSummary(long id, String candidateName, String title, String status) {
    }

    public record AssessmentWrite(
            long applicationId, String type, OffsetDateTime scheduledAt, String venue,
            Long scheduledBy, BigDecimal score, Boolean passed, String notes, Long recordedBy
    ) {
    }

    public record AssessmentData(
            long id, long applicationId, String type, OffsetDateTime scheduledAt,
            String venue, BigDecimal score, Boolean passed, String notes,
            String candidateName, String jobTitle, String dept,
            Long scheduledBy, Long recordedBy
    ) {
    }
}
