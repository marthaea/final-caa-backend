package ug.go.caa.recruitment.feature.recruitment.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CriteriaRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public CriteriaRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<CriteriaData> findByJobId(long jobId) {
        return jdbc.sql("SELECT * FROM criteria WHERE job_id = :jobId")
                .param("jobId", jobId)
                .query(this::map)
                .optional();
    }

    public Optional<String> jobTitle(long jobId) {
        return jdbc.sql("SELECT title FROM jobs WHERE id = :jobId")
                .param("jobId", jobId)
                .query(String.class)
                .optional();
    }

    public CriteriaData save(long jobId, CriteriaWrite criteria) {
        jdbc.sql("""
                INSERT INTO criteria (
                    job_id, min_cgpa, min_experience_years, required_qual_level,
                    required_keywords, disqualifying_universities, screening_questions,
                    assessment_types, requirements, notes
                ) VALUES (
                    :jobId, :minCgpa, :minExperienceYears, :requiredQualLevel,
                    CAST(:requiredKeywords AS jsonb), CAST(:disqualifyingUniversities AS jsonb),
                    CAST(:screeningQuestions AS jsonb), CAST(:assessmentTypes AS jsonb),
                    CAST(:requirements AS jsonb), :notes
                )
                ON CONFLICT (job_id) DO UPDATE SET
                    min_cgpa = excluded.min_cgpa,
                    min_experience_years = excluded.min_experience_years,
                    required_qual_level = excluded.required_qual_level,
                    required_keywords = excluded.required_keywords,
                    disqualifying_universities = excluded.disqualifying_universities,
                    screening_questions = excluded.screening_questions,
                    assessment_types = excluded.assessment_types,
                    requirements = excluded.requirements,
                    notes = excluded.notes,
                    updated_at = now()
                """)
                .param("jobId", jobId)
                .param("minCgpa", criteria.minCgpa())
                .param("minExperienceYears", criteria.minExperienceYears())
                .param("requiredQualLevel", criteria.requiredQualLevel())
                .param("requiredKeywords", json(criteria.requiredKeywords()))
                .param("disqualifyingUniversities", json(criteria.disqualifyingUniversities()))
                .param("screeningQuestions", json(criteria.screeningQuestions()))
                .param("assessmentTypes", json(criteria.assessmentTypes()))
                .param("requirements", json(criteria.requirements()))
                .param("notes", criteria.notes())
                .update();
        return findByJobId(jobId).orElseThrow();
    }

    private CriteriaData map(ResultSet row, int rowNumber) throws SQLException {
        return new CriteriaData(
                row.getLong("job_id"), row.getBigDecimal("min_cgpa"),
                nullableInteger(row, "min_experience_years"), row.getString("required_qual_level"),
                jsonNode(row.getString("required_keywords")),
                jsonNode(row.getString("disqualifying_universities")),
                jsonNode(row.getString("screening_questions")),
                jsonNode(row.getString("assessment_types")),
                jsonNode(row.getString("requirements")), row.getString("notes"));
    }

    private JsonNode jsonNode(String value) {
        try {
            return mapper.readTree(value == null ? "[]" : value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid criteria JSON", exception);
        }
    }

    private String json(JsonNode value) {
        return value == null ? "[]" : value.toString();
    }

    private static Integer nullableInteger(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }

    public record CriteriaWrite(
            BigDecimal minCgpa,
            Integer minExperienceYears,
            String requiredQualLevel,
            JsonNode requiredKeywords,
            JsonNode disqualifyingUniversities,
            JsonNode screeningQuestions,
            JsonNode assessmentTypes,
            JsonNode requirements,
            String notes
    ) {
    }

    public record CriteriaData(
            long jobId,
            BigDecimal minCgpa,
            Integer minExperienceYears,
            String requiredQualLevel,
            JsonNode requiredKeywords,
            JsonNode disqualifyingUniversities,
            JsonNode screeningQuestions,
            JsonNode assessmentTypes,
            JsonNode requirements,
            String notes
    ) {
    }
}
