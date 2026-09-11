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
public class CandidateScoreRepository {

    private final JdbcClient jdbc;

    public CandidateScoreRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScoredApplication> applications(long jobId, String status) {
        if (status == null || status.isBlank()) {
            return jdbc.sql("""
                    SELECT id, candidate_name, candidate_email, status
                    FROM applications WHERE job_id = :jobId
                    """)
                    .param("jobId", jobId).query(this::mapApplication).list();
        }
        return jdbc.sql("""
                SELECT id, candidate_name, candidate_email, status
                FROM applications WHERE job_id = :jobId AND status = :status
                """)
                .param("jobId", jobId).param("status", status)
                .query(this::mapApplication).list();
    }

    public List<ScoreData> scores(List<Long> applicationIds) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT cs.*, concat(u.first_name, ' ', u.last_name) AS scorer_name,
                    u.email AS scorer_email
                FROM candidate_scores cs
                JOIN users u ON u.id = cs.scorer_user_id
                WHERE cs.application_id IN (:ids)
                """)
                .param("ids", applicationIds).query(this::mapScore).list();
    }

    public Optional<String> candidateName(long applicationId) {
        return jdbc.sql("SELECT candidate_name FROM applications WHERE id = :id")
                .param("id", applicationId).query(String.class).optional();
    }

    public ScoreData save(
            long applicationId, long scorerId, BigDecimal score, String comment
    ) {
        jdbc.sql("""
                INSERT INTO candidate_scores (application_id, scorer_user_id, score, comment)
                VALUES (:applicationId, :scorerId, :score, :comment)
                ON CONFLICT (application_id, scorer_user_id) DO UPDATE SET
                    score = excluded.score, comment = excluded.comment, updated_at = now()
                """)
                .param("applicationId", applicationId).param("scorerId", scorerId)
                .param("score", score).param("comment", comment).update();
        return jdbc.sql("""
                SELECT cs.*, concat(u.first_name, ' ', u.last_name) AS scorer_name,
                    u.email AS scorer_email
                FROM candidate_scores cs JOIN users u ON u.id = cs.scorer_user_id
                WHERE cs.application_id = :applicationId AND cs.scorer_user_id = :scorerId
                """)
                .param("applicationId", applicationId).param("scorerId", scorerId)
                .query(this::mapScore).single();
    }

    private ScoredApplication mapApplication(ResultSet row, int number) throws SQLException {
        return new ScoredApplication(
                row.getLong("id"), row.getString("candidate_name"),
                row.getString("candidate_email"), row.getString("status"));
    }

    private ScoreData mapScore(ResultSet row, int number) throws SQLException {
        return new ScoreData(
                row.getLong("id"), row.getLong("application_id"),
                row.getLong("scorer_user_id"), row.getString("scorer_name"),
                row.getString("scorer_email"), row.getBigDecimal("score"),
                row.getString("comment"), row.getObject("updated_at", OffsetDateTime.class));
    }

    public record ScoredApplication(
            long id, String candidateName, String candidateEmail, String status
    ) {
    }

    public record ScoreData(
            long id, long applicationId, long scorerUserId, String scorerName,
            String scorerEmail, BigDecimal score, String comment, OffsetDateTime updatedAt
    ) {
    }
}
