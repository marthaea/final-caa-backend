package ug.go.caa.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import ug.go.caa.recruitment.shared.security.TokenService;
import ug.go.caa.recruitment.shared.security.TokenService.UserTokenDetails;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CandidateRecruitmentApiIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenService tokens;

    @Autowired
    ObjectMapper mapper;

    long candidateId;
    long adminId;
    long jobId;
    String candidateToken;
    String adminToken;

    @BeforeEach
    void seed() {
        jdbc.execute("""
                TRUNCATE users, jobs, settings, applications, cv_profiles, criteria,
                    candidate_scores, assessments, notifications, audit_log, outbox_events
                RESTART IDENTITY CASCADE
                """);
        candidateId = user("candidate@example.org", "Ada", "Candidate", "external", null);
        adminId = user("admin@example.org", "System", "Admin", "admin", "super");
        candidateToken = token(candidateId, "candidate@example.org", "Ada", "Candidate", "external", null);
        adminToken = token(adminId, "admin@example.org", "System", "Admin", "admin", "super");
        jdbc.update("INSERT INTO settings (max_applications_per_candidate) VALUES (5)");
        jobId = jdbc.queryForObject("""
                INSERT INTO jobs (
                    abbr, title, dept, dept_key, location, salary, salary_band, type,
                    closes, closes_at, visibility, required_qualification, status
                ) VALUES (
                    'OPS', 'Pilot "I"', 'Operations', 'ops', 'Entebbe', 'UGX',
                    'UG3', 'Full-time', 'Dec 31, 2099', DATE '2099-12-31',
                    'external', 'Degree', 'published'
                ) RETURNING id
                """, Long.class);
    }

    @Test
    void applicationsPreserveScreeningOwnershipCsvAndStatusSideEffects() throws Exception {
        jdbc.update("""
                INSERT INTO criteria (
                    job_id, min_cgpa, disqualifying_universities,
                    screening_questions, required_keywords
                ) VALUES (?, 3.00, '["Blocked University"]', '[]', '[]')
                """, jobId);

        long applicationId = dataId(mvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(candidateToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": %d,
                                  "completion": 75,
                                  "cgpa": 2.5,
                                  "university": "Makerere"
                                }
                                """.formatted(jobId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("Declined"))
                .andExpect(jsonPath("$.data.candidateEmail").value("candidate@example.org"))
                .andReturn());

        mvc.perform(get("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(candidateToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(applicationId));

        MvcResult csv = mvc.perform(get("/api/applications/export")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.matchesPattern(
                                "attachment; filename=\"applications_[0-9]+\\.csv\"")))
                .andReturn();
        assertThat(csv.getResponse().getContentAsString())
                .contains("\r\n")
                .contains("\"Pilot \"\"I\"\"\"");

        mvc.perform(put("/api/applications/{id}/status", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"Offered",
                                  "notifyEmail":"candidate@example.org",
                                  "notifyMessage":"Congratulations"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Offered"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class))
                .isEqualTo(2);

        mvc.perform(put("/api/applications/{id}/deployment", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentStation\":\"Entebbe\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/applications/{id}/deployment", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deploymentStation":"Entebbe","deploymentDate":"2026-10-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deploymentDate").value("2026-10-01"));

        mvc.perform(put("/api/applications/bulk-status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"updates":[{"id":%d,"status":"Pending"}]}
                                """.formatted(applicationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1));
        mvc.perform(delete("/api/applications/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(candidateToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Withdrawn"));
    }

    @Test
    void cvAndCandidateScoresMatchCandidateAndPanelContracts() throws Exception {
        mvc.perform(put("/api/cv")
                        .header(HttpHeaders.AUTHORIZATION, bearer(candidateToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "personal":{"phone":"0700"},
                                  "highestLevel":"Degree",
                                  "skills":["Java"],
                                  "qualifications":[],
                                  "experience":[],
                                  "referees":[],
                                  "nextOfKin":{"name":"Grace"},
                                  "photoFile":"https://files/photo.webp"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personal.phone").value("0700"));
        mvc.perform(get("/api/cv")
                        .header(HttpHeaders.AUTHORIZATION, bearer(candidateToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills[0]").value("Java"));
        mvc.perform(get("/api/cv/by-email/{email}", "CANDIDATE@example.org")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.highestLevel").value("Degree"));
        mvc.perform(get("/api/cv/by-email/{email}", "other@example.org")
                        .header(HttpHeaders.AUTHORIZATION, bearer(candidateToken)))
                .andExpect(status().isForbidden());

        long applicationId = application("Under Review");
        mvc.perform(put("/api/candidate-scores/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":80,\"comment\":\"Strong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scorerEmail").value("admin@example.org"));

        long secondAdmin = user("panel@example.org", "Panel", "Member", "admin", "super");
        mvc.perform(put("/api/candidate-scores/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(token(secondAdmin, "panel@example.org", "Panel", "Member", "admin", "super")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":90}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/candidate-scores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("jobId", String.valueOf(jobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].scores.length()").value(2))
                .andExpect(jsonPath("$.data[0].average").value(85.0));
    }

    @Test
    void assessmentsEnforcePostgresSafeResultsAndAdvanceApplication() throws Exception {
        long applicationId = application("Interview");
        mvc.perform(put("/api/assessments/{id}/written", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"2026-10-01T09:00:00Z","venue":"Room 1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-10-01T09:00:00Z"));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, applicationId))
                .isEqualTo("Assessment Scheduled");

        mvc.perform(put("/api/assessments/{id}/written", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"partial result\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "score and passed must both be provided when recording an assessment"));

        mvc.perform(put("/api/assessments/{id}/written", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":72.5,\"passed\":true,\"notes\":\"Pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, applicationId))
                .isEqualTo("Assessment Complete");

        mvc.perform(get("/api/assessments/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].score").value(72.5));
        mvc.perform(get("/api/assessments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].candidateName").value("Ada Candidate"))
                .andExpect(jsonPath("$.data[0].jobTitle").value("Pilot \"I\""));
    }

    private long application(String status) {
        return jdbc.queryForObject("""
                INSERT INTO applications (
                    job_id, candidate_user_id, candidate_email, candidate_name,
                    abbr, title, dept, date, status, completion
                ) VALUES (?, ?, 'candidate@example.org', 'Ada Candidate',
                    'OPS', 'Pilot "I"', 'Operations', 'Sep 9, 2026', ?, 100)
                RETURNING id
                """, Long.class, jobId, candidateId, status);
    }

    private long user(String email, String firstName, String lastName, String type, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (
                    email, password_hash, first_name, last_name,
                    account_type, admin_role, effective_type, email_verified
                ) VALUES (?, 'hash', ?, ?, ?, ?, ?, true) RETURNING id
                """, Long.class, email, firstName, lastName, type, role, type);
    }

    private String token(
            long id, String email, String firstName, String lastName, String type, String role
    ) {
        return tokens.accessToken(new UserTokenDetails(
                id, email, firstName, lastName, type, role, null, type, 0));
    }

    private long dataId(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("id").asLong();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
