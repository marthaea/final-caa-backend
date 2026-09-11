package ug.go.caa.recruitment.feature.recruitment.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ApplicationRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public ApplicationRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<ApplicationData> find(
            Long jobId, String status, LocalDate fromDate, LocalDate toDate,
            String email, Integer limit, Integer offset
    ) {
        StringBuilder sql = new StringBuilder("SELECT * FROM applications");
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> conditions = new ArrayList<>();
        if (jobId != null) {
            conditions.add("job_id = :jobId");
            params.put("jobId", jobId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = :status");
            params.put("status", status);
        }
        if (fromDate != null) {
            conditions.add("applied_at >= :fromDate");
            params.put("fromDate", fromDate);
        }
        if (toDate != null) {
            conditions.add("applied_at <= :toDate");
            params.put("toDate", toDate);
        }
        if (email != null && !email.isBlank()) {
            conditions.add("candidate_email ILIKE :email");
            params.put("email", "%" + email + "%");
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY applied_at DESC LIMIT :limit OFFSET :offset");
        params.put("limit", limit == null || limit == 0 ? 500 : Math.max(0, limit));
        params.put("offset", offset == null ? 0 : Math.max(0, offset));
        JdbcClient.StatementSpec query = jdbc.sql(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query = query.param(entry.getKey(), entry.getValue());
        }
        return query.query(this::map).list();
    }

    public List<ApplicationData> findOwn(String email) {
        return jdbc.sql("""
                SELECT * FROM applications
                WHERE lower(candidate_email) = lower(:email)
                ORDER BY applied_at DESC
                """)
                .param("email", email)
                .query(this::map)
                .list();
    }

    public List<ApplicationData> export(
            Long jobId, String status, LocalDate fromDate, LocalDate toDate, String email
    ) {
        return find(jobId, status, fromDate, toDate, email, 5000, 0);
    }

    public Optional<ApplicationData> findById(long id) {
        return jdbc.sql("SELECT * FROM applications WHERE id = :id")
                .param("id", id).query(this::map).optional();
    }

    public Optional<ApplicationData> findOwned(long id, String email) {
        return jdbc.sql("""
                SELECT * FROM applications
                WHERE id = :id AND lower(candidate_email) = lower(:email)
                """)
                .param("id", id).param("email", email).query(this::map).optional();
    }

    public Optional<JobForApplication> acceptingJob(long id) {
        return jdbc.sql("""
                SELECT id, abbr, title, dept FROM jobs
                WHERE id = :id AND closes_at >= current_date
                """)
                .param("id", id)
                .query((row, number) -> new JobForApplication(
                        row.getLong("id"), row.getString("abbr"),
                        row.getString("title"), row.getString("dept")))
                .optional();
    }

    public boolean jobExists(long id) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM jobs WHERE id = :id)")
                .param("id", id).query(Boolean.class).single();
    }

    public boolean duplicate(long jobId, String email) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM applications
                    WHERE job_id = :jobId AND lower(candidate_email) = lower(:email))
                """)
                .param("jobId", jobId).param("email", email).query(Boolean.class).single();
    }

    public int activeCount(String email) {
        return jdbc.sql("""
                SELECT count(*) FROM applications
                WHERE lower(candidate_email) = lower(:email) AND status <> 'Declined'
                """)
                .param("email", email).query(Integer.class).single();
    }

    public int applicationLimit() {
        return jdbc.sql("SELECT max_applications_per_candidate FROM settings ORDER BY id LIMIT 1")
                .query(Integer.class).optional().orElse(5);
    }

    public Optional<ScreeningCriteria> criteria(long jobId) {
        return jdbc.sql("""
                SELECT min_cgpa, disqualifying_universities, screening_questions
                FROM criteria WHERE job_id = :jobId
                """)
                .param("jobId", jobId)
                .query((row, number) -> new ScreeningCriteria(
                        row.getBigDecimal("min_cgpa"),
                        json(row.getString("disqualifying_universities"), "[]"),
                        json(row.getString("screening_questions"), "[]")))
                .optional();
    }

    public ApplicationData create(
            JobForApplication job, long userId, String email, String name, String date,
            String status, int completion, BigDecimal cgpa, String university, JsonNode answers
    ) {
        long id = jdbc.sql("""
                INSERT INTO applications (
                    job_id, candidate_user_id, candidate_email, candidate_name,
                    abbr, title, dept, date, status, completion, cgpa, university,
                    screening_answers
                ) VALUES (
                    :jobId, :userId, :email, :name, :abbr, :title, :dept, :date,
                    :status, :completion, :cgpa, :university, CAST(:answers AS jsonb)
                ) RETURNING id
                """)
                .param("jobId", job.id()).param("userId", userId)
                .param("email", email).param("name", name).param("abbr", job.abbr())
                .param("title", job.title()).param("dept", job.dept()).param("date", date)
                .param("status", status).param("completion", completion).param("cgpa", cgpa)
                .param("university", university)
                .param("answers", answers == null ? null : answers.toString())
                .query(Long.class).single();
        return findById(id).orElseThrow();
    }

    public void updateStatus(long id, String status) {
        jdbc.sql("UPDATE applications SET status = :status, updated_at = now() WHERE id = :id")
                .param("status", status).param("id", id).update();
    }

    public void updateStatuses(List<StatusChange> changes) {
        changes.forEach(change -> updateStatus(change.id(), change.status()));
    }

    public void updateDeployment(long id, String station, LocalDate date) {
        jdbc.sql("""
                UPDATE applications SET deployment_station = :station,
                    deployment_date = :date, updated_at = now() WHERE id = :id
                """)
                .param("station", station).param("date", date).param("id", id).update();
    }

    public void addNotification(
            Long recipientId, String email, String title, String message, String type
    ) {
        jdbc.sql("""
                INSERT INTO notifications (
                    recipient_user_id, recipient_email, title, message, type
                ) VALUES (:recipientId, :email, :title, :message, :type)
                """)
                .param("recipientId", recipientId).param("email", email)
                .param("title", title).param("message", message).param("type", type).update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM applications WHERE id = :id").param("id", id).update();
    }

    private ApplicationData map(ResultSet row, int number) throws SQLException {
        return new ApplicationData(
                row.getLong("id"), row.getLong("job_id"), row.getString("abbr"),
                row.getString("title"), row.getString("dept"), row.getString("date"),
                row.getString("status"), row.getInt("completion"),
                row.getString("candidate_name"), row.getString("candidate_email"),
                row.getBigDecimal("cgpa"), row.getString("university"),
                json(row.getString("screening_answers"), null),
                row.getString("deployment_station"),
                row.getObject("deployment_date", LocalDate.class),
                nullableLong(row, "candidate_user_id"));
    }

    private JsonNode json(String value, String fallback) {
        try {
            if (value == null && fallback == null) {
                return null;
            }
            return mapper.readTree(value == null ? fallback : value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid application JSON", exception);
        }
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    public record JobForApplication(long id, String abbr, String title, String dept) {
    }

    public record ScreeningCriteria(
            BigDecimal minCgpa, JsonNode disqualifyingUniversities, JsonNode screeningQuestions
    ) {
    }

    public record StatusChange(long id, String status) {
    }

    public record ApplicationData(
            long id, long jobId, String abbr, String title, String dept, String date,
            String status, int completion, String candidateName, String candidateEmail,
            BigDecimal cgpa, String university, JsonNode screeningAnswers,
            String deploymentStation, LocalDate deploymentDate, Long candidateUserId
    ) {
    }
}
