package ug.go.caa.recruitment.feature.recruitment.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JobRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JobRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public boolean allowsCrossVisibility() {
        return jdbc.sql("SELECT allow_external_internal_jobs FROM settings ORDER BY id LIMIT 1")
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    public List<JobData> findVisible(boolean admin, String effectiveType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM jobs");
        List<String> conditions = new ArrayList<>();
        if (!admin) {
            conditions.add("status = 'published'");
            conditions.add("closes_at >= current_date");
        }
        if (!allowsCrossVisibility() && !"internal".equals(effectiveType) && !"admin".equals(effectiveType)) {
            conditions.add("visibility = 'external'");
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY featured DESC, created_at DESC");
        return jdbc.sql(sql.toString()).query(this::map).list();
    }

    public Optional<JobData> findById(long id) {
        return jdbc.sql("SELECT * FROM jobs WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public JobData create(JobWrite job, long actorId) {
        long id = jdbc.sql("""
                INSERT INTO jobs (
                    abbr, title, dept, dept_key, location, salary, salary_band, type,
                    closes, closes_at, visibility, min_age, required_experience,
                    required_qualification, description, featured, created_by, status,
                    department_id, job_ref, reports_to, vacancies, about_role,
                    accountabilities, special_skills
                ) VALUES (
                    :abbr, :title, :dept, :deptKey, :location, :salary, :salaryBand, :type,
                    :closes, :closesAt, :visibility, :minAge, :requiredExperience,
                    :requiredQualification, :description, :featured, :actorId, 'draft',
                    :departmentId, :jobRef, :reportsTo, :vacancies, :aboutRole,
                    CAST(:accountabilities AS jsonb), CAST(:specialSkills AS jsonb)
                ) RETURNING id
                """)
                .params(requiredParams(job))
                .param("abbr", abbreviation(job.title()))
                .param("actorId", actorId)
                .param("description", job.description())
                .param("departmentId", job.departmentId())
                .param("jobRef", job.jobRef())
                .param("reportsTo", job.reportsTo())
                .param("aboutRole", job.aboutRole())
                .query(Long.class)
                .single();
        return findById(id).orElseThrow();
    }

    public JobData update(long id, JobWrite job) {
        Map<String, Object> values = optionalParams(job);
        if (values.isEmpty()) {
            return findById(id).orElseThrow();
        }
        String assignments = values.keySet().stream()
                .map(key -> column(key) + " = " + (isJson(key) ? "CAST(:" + key + " AS jsonb)" : ":" + key))
                .collect(java.util.stream.Collectors.joining(", "));
        JdbcClient.StatementSpec statement = jdbc.sql(
                "UPDATE jobs SET " + assignments + ", updated_at = now() WHERE id = :id")
                .param("id", id);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            statement = statement.param(entry.getKey(), entry.getValue());
        }
        statement.update();
        return findById(id).orElseThrow();
    }

    public void setWorkflow(
            long id,
            String status,
            Long reviewedBy,
            Long approvedBy,
            String declineReason
    ) {
        jdbc.sql("""
                UPDATE jobs SET status = :status,
                    reviewed_by = COALESCE(:reviewedBy, reviewed_by),
                    approved_by = COALESCE(:approvedBy, approved_by),
                    decline_reason = :declineReason,
                    updated_at = now()
                WHERE id = :id
                """)
                .param("status", status)
                .param("reviewedBy", reviewedBy)
                .param("approvedBy", approvedBy)
                .param("declineReason", declineReason)
                .param("id", id)
                .update();
    }

    public boolean isDepartmentHead(long departmentId, long userId) {
        return jdbc.sql("""
                SELECT EXISTS(
                    SELECT 1 FROM departments WHERE id = :departmentId AND head_user_id = :userId
                )
                """)
                .param("departmentId", departmentId)
                .param("userId", userId)
                .query(Boolean.class)
                .single();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM jobs WHERE id = :id").param("id", id).update();
    }

    private Map<String, Object> requiredParams(JobWrite job) {
        Map<String, Object> values = new HashMap<>();
        values.put("title", job.title());
        values.put("dept", job.dept());
        values.put("deptKey", job.deptKey());
        values.put("location", job.location());
        values.put("salary", job.salary());
        values.put("salaryBand", job.salaryBand());
        values.put("type", job.type());
        values.put("closes", job.closes());
        values.put("closesAt", job.closesAt());
        values.put("visibility", job.visibility());
        values.put("minAge", job.minAge() == null ? 21 : job.minAge());
        values.put("requiredExperience", job.requiredExperience() == null ? 0 : job.requiredExperience());
        values.put("requiredQualification", job.requiredQualification());
        values.put("featured", Boolean.TRUE.equals(job.featured()));
        values.put("vacancies", job.vacancies() == null ? 1 : job.vacancies());
        values.put("accountabilities", json(job.accountabilities(), "[]"));
        values.put("specialSkills", json(job.specialSkills(), "[]"));
        return values;
    }

    private Map<String, Object> optionalParams(JobWrite job) {
        Map<String, Object> values = new HashMap<>();
        put(values, "title", job.title());
        put(values, "dept", job.dept());
        put(values, "deptKey", job.deptKey());
        put(values, "location", job.location());
        put(values, "salary", job.salary());
        put(values, "salaryBand", job.salaryBand());
        put(values, "type", job.type());
        put(values, "closes", job.closes());
        put(values, "closesAt", job.closesAt());
        put(values, "visibility", job.visibility());
        put(values, "minAge", job.minAge());
        put(values, "requiredExperience", job.requiredExperience());
        put(values, "requiredQualification", job.requiredQualification());
        put(values, "description", job.description());
        put(values, "featured", job.featured());
        put(values, "departmentId", job.departmentId());
        put(values, "jobRef", job.jobRef());
        put(values, "reportsTo", job.reportsTo());
        put(values, "vacancies", job.vacancies());
        put(values, "aboutRole", job.aboutRole());
        if (job.accountabilities() != null) {
            values.put("accountabilities", json(job.accountabilities(), "[]"));
        }
        if (job.specialSkills() != null) {
            values.put("specialSkills", json(job.specialSkills(), "[]"));
        }
        return values;
    }

    private JobData map(ResultSet row, int rowNumber) throws SQLException {
        return new JobData(
                row.getLong("id"), row.getString("abbr"), row.getString("title"),
                row.getString("dept"), row.getString("dept_key"), row.getString("location"),
                row.getString("salary"), row.getString("salary_band"), row.getString("type"),
                row.getString("closes"), row.getObject("closes_at", LocalDate.class),
                row.getString("visibility"), row.getInt("min_age"),
                row.getInt("required_experience"), row.getString("required_qualification"),
                row.getString("description"), row.getBoolean("featured"), row.getString("status"),
                nullableLong(row, "department_id"), row.getString("decline_reason"),
                row.getString("job_ref"), row.getString("reports_to"), row.getInt("vacancies"),
                row.getString("about_role"), jsonNode(row.getString("accountabilities"), true),
                jsonNode(row.getString("special_skills"), true), nullableLong(row, "created_by"));
    }

    private JsonNode jsonNode(String value, boolean array) {
        try {
            return value == null ? mapper.readTree(array ? "[]" : "{}") : mapper.readTree(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid JSON stored in jobs", exception);
        }
    }

    private String json(JsonNode value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private static boolean isJson(String key) {
        return "accountabilities".equals(key) || "specialSkills".equals(key);
    }

    private static String column(String key) {
        return key.replaceAll("([A-Z])", "_$1").toLowerCase();
    }

    private static String abbreviation(String title) {
        String first = title.trim().split("\\s+")[0];
        return first.substring(0, Math.min(3, first.length())).toUpperCase();
    }

    public record JobWrite(
            String title,
            String dept,
            String deptKey,
            String location,
            String salary,
            String salaryBand,
            String type,
            String closes,
            LocalDate closesAt,
            String visibility,
            Integer minAge,
            Integer requiredExperience,
            String requiredQualification,
            String description,
            Boolean featured,
            Long departmentId,
            String jobRef,
            String reportsTo,
            Integer vacancies,
            String aboutRole,
            JsonNode accountabilities,
            JsonNode specialSkills
    ) {
    }

    public record JobData(
            long id,
            String abbr,
            String title,
            String dept,
            String deptKey,
            String location,
            String salary,
            String salaryBand,
            String type,
            String closes,
            LocalDate closesAt,
            String visibility,
            int minAge,
            int requiredExperience,
            String requiredQualification,
            String description,
            boolean featured,
            String status,
            Long departmentId,
            String declineReason,
            String jobRef,
            String reportsTo,
            int vacancies,
            String aboutRole,
            JsonNode accountabilities,
            JsonNode specialSkills,
            Long createdBy
    ) {
    }
}
