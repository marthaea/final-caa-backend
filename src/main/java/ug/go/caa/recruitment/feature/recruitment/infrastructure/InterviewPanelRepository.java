package ug.go.caa.recruitment.feature.recruitment.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InterviewPanelRepository {

    private final JdbcClient jdbc;

    public InterviewPanelRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> jobTitle(long jobId) {
        return jdbc.sql("SELECT title FROM jobs WHERE id = :jobId")
                .param("jobId", jobId)
                .query(String.class)
                .optional();
    }

    public Optional<StaffContact> staffContact(long staffId) {
        return jdbc.sql("SELECT first_name, last_name, email FROM staff WHERE id = :staffId")
                .param("staffId", staffId)
                .query((row, n) -> new StaffContact(
                        row.getString("first_name"), row.getString("last_name"), row.getString("email")))
                .optional();
    }

    public boolean isMember(long jobId, long staffId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM interview_panel_members WHERE job_id = :jobId AND staff_id = :staffId)
                """)
                .param("jobId", jobId).param("staffId", staffId)
                .query(Boolean.class).single();
    }

    public List<PanelMember> findByJob(long jobId) {
        return jdbc.sql("""
                SELECT m.id, m.job_id, m.staff_id, m.invited_by_name, m.invited_at,
                       s.first_name, s.last_name, s.email, s.dept, s.position
                FROM interview_panel_members m
                JOIN staff s ON s.id = m.staff_id
                WHERE m.job_id = :jobId
                ORDER BY m.invited_at DESC
                """)
                .param("jobId", jobId)
                .query((row, n) -> new PanelMember(
                        row.getLong("id"), row.getLong("job_id"), row.getLong("staff_id"),
                        row.getString("first_name"), row.getString("last_name"),
                        row.getString("email"), row.getString("dept"), row.getString("position"),
                        row.getString("invited_by_name"),
                        row.getObject("invited_at", java.time.OffsetDateTime.class).toInstant()))
                .list();
    }

    public PanelMember add(long jobId, long staffId, Long invitedBy, String invitedByName) {
        long id = jdbc.sql("""
                INSERT INTO interview_panel_members (job_id, staff_id, invited_by, invited_by_name)
                VALUES (:jobId, :staffId, :invitedBy, :invitedByName) RETURNING id
                """)
                .param("jobId", jobId).param("staffId", staffId)
                .param("invitedBy", invitedBy).param("invitedByName", invitedByName)
                .query(Long.class).single();
        return findByJob(jobId).stream().filter(m -> m.id() == id).findFirst().orElseThrow();
    }

    public boolean remove(long id, long jobId) {
        int updated = jdbc.sql("DELETE FROM interview_panel_members WHERE id = :id AND job_id = :jobId")
                .param("id", id).param("jobId", jobId)
                .update();
        return updated > 0;
    }

    public record StaffContact(String firstName, String lastName, String email) {
    }

    public record PanelMember(
            long id, long jobId, long staffId,
            String firstName, String lastName, String email, String dept, String position,
            String invitedByName, Instant invitedAt
    ) {
    }
}
