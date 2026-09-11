package ug.go.caa.recruitment.feature.recruitment.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CvRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public CvRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<CvData> find(String email) {
        return jdbc.sql("SELECT * FROM cv_profiles WHERE lower(user_email) = lower(:email)")
                .param("email", email).query(this::map).optional();
    }

    public CvData save(long userId, String email, CvWrite cv) {
        jdbc.sql("""
                INSERT INTO cv_profiles (
                    user_id, user_email, personal_data, highest_level, qualifications,
                    skills, experience, referees, next_of_kin, photo_url
                ) VALUES (
                    :userId, :email, CAST(:personal AS jsonb), :highestLevel,
                    CAST(:qualifications AS jsonb), CAST(:skills AS jsonb),
                    CAST(:experience AS jsonb), CAST(:referees AS jsonb),
                    CAST(:nextOfKin AS jsonb), :photoFile
                )
                ON CONFLICT (user_email) DO UPDATE SET
                    user_id = excluded.user_id,
                    personal_data = excluded.personal_data,
                    highest_level = excluded.highest_level,
                    qualifications = excluded.qualifications,
                    skills = excluded.skills,
                    experience = excluded.experience,
                    referees = excluded.referees,
                    next_of_kin = excluded.next_of_kin,
                    photo_url = excluded.photo_url,
                    updated_at = now()
                """)
                .param("userId", userId).param("email", email.toLowerCase())
                .param("personal", json(cv.personal(), "{}"))
                .param("highestLevel", blankToNull(cv.highestLevel()))
                .param("qualifications", json(cv.qualifications(), "[]"))
                .param("skills", json(cv.skills(), "[]"))
                .param("experience", json(cv.experience(), "[]"))
                .param("referees", json(cv.referees(), "[]"))
                .param("nextOfKin", json(cv.nextOfKin(), "{}"))
                .param("photoFile", blankToNull(cv.photoFile()))
                .update();
        return find(email).orElseThrow();
    }

    private CvData map(ResultSet row, int number) throws SQLException {
        return new CvData(
                parse(row.getString("personal_data"), "{}"),
                row.getString("highest_level"),
                parse(row.getString("qualifications"), "[]"),
                parse(row.getString("skills"), "[]"),
                parse(row.getString("experience"), "[]"),
                parse(row.getString("referees"), "[]"),
                parse(row.getString("next_of_kin"), "{}"),
                row.getString("photo_url"));
    }

    private JsonNode parse(String value, String fallback) {
        try {
            return mapper.readTree(value == null ? fallback : value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid CV JSON", exception);
        }
    }

    private static String json(JsonNode value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record CvWrite(
            JsonNode personal, String highestLevel, JsonNode qualifications,
            JsonNode skills, JsonNode experience, JsonNode referees,
            JsonNode nextOfKin, String photoFile
    ) {
    }

    public record CvData(
            JsonNode personal, String highestLevel, JsonNode qualifications,
            JsonNode skills, JsonNode experience, JsonNode referees,
            JsonNode nextOfKin, String photoFile
    ) {
    }
}
