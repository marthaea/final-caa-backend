package ug.go.caa.recruitment.feature.recruitment.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JobTemplateRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JobTemplateRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<JobTemplate> findAll() {
        return jdbc.sql("SELECT * FROM job_templates ORDER BY name ASC")
                .query(this::map)
                .list();
    }

    public Optional<JobTemplate> findById(long id) {
        return jdbc.sql("SELECT * FROM job_templates WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public JobTemplate create(
            String name,
            Long departmentId,
            Long sourceJobId,
            JsonNode content,
            long actorId
    ) {
        long id = jdbc.sql("""
                INSERT INTO job_templates (name, department_id, source_job_id, content, created_by)
                VALUES (:name, :departmentId, :sourceJobId, CAST(:content AS jsonb), :actorId)
                RETURNING id
                """)
                .param("name", name)
                .param("departmentId", departmentId)
                .param("sourceJobId", sourceJobId)
                .param("content", content.toString())
                .param("actorId", actorId)
                .query(Long.class)
                .single();
        return findById(id).orElseThrow();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM job_templates WHERE id = :id").param("id", id).update();
    }

    private JobTemplate map(ResultSet row, int rowNumber) throws SQLException {
        return new JobTemplate(
                row.getLong("id"), row.getString("name"),
                nullableLong(row, "department_id"), nullableLong(row, "source_job_id"),
                json(row.getString("content")),
                row.getObject("created_at", java.time.OffsetDateTime.class).toInstant());
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid template JSON", exception);
        }
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    public record JobTemplate(
            long id,
            String name,
            Long departmentId,
            Long sourceJobId,
            JsonNode content,
            Instant createdAt
    ) {
    }
}
