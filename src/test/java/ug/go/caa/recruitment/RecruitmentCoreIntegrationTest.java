package ug.go.caa.recruitment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class RecruitmentCoreIntegrationTest {

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

    String superToken;

    @BeforeEach
    void cleanAndAuthenticate() {
        jdbc.execute("""
                TRUNCATE users, departments, jobs, criteria, job_templates,
                    outbox_events, audit_log RESTART IDENTITY CASCADE
                """);
        superToken = adminToken("super@example.org", "super");
    }

    @Test
    void jobApprovalWorkflowControlsPublicVisibility() throws Exception {
        long jobId = createJob();

        mvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/jobs/{id}/submit-for-review", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending_review"));
        mvc.perform(put("/api/jobs/{id}/review", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending_approval"));
        mvc.perform(put("/api/jobs/{id}/approve", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"));

        mvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Air Traffic Controller"));
        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void departmentCriteriaAndTemplatesPreserveContracts() throws Exception {
        MvcResult department = mvc.perform(post("/api/departments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Information Technology\",\"code\":\"IT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("IT"))
                .andReturn();
        long departmentId = dataId(department);
        long jobId = createJob(departmentId);

        mvc.perform(put("/api/criteria/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minCgpa":3.2,
                                  "requiredKeywords":["aviation"],
                                  "screeningQuestions":[{"question":"Licensed?"}],
                                  "requirements":[
                                    {"label":"Degree","usage":"display"},
                                    {"label":"Internal score","usage":"criteriaOnly"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minCgpa").value(3.2));
        mvc.perform(get("/api/criteria/{jobId}/public", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requirements.length()").value(1))
                .andExpect(jsonPath("$.data.requirements[0].label").value("Degree"));

        MvcResult template = mvc.perform(post("/api/job-templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"IT Vacancy","departmentId":%d,"content":{"title":"Engineer"}}
                                """.formatted(departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content.title").value("Engineer"))
                .andReturn();
        mvc.perform(delete("/api/job-templates/{id}", dataId(template))
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Deleted"));
    }

    private long createJob() throws Exception {
        return createJob(null);
    }

    private long createJob(Long departmentId) throws Exception {
        String department = departmentId == null ? "" : ",\"departmentId\":" + departmentId;
        MvcResult result = mvc.perform(post("/api/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Air Traffic Controller",
                                  "dept":"Operations",
                                  "deptKey":"operations",
                                  "location":"Entebbe",
                                  "salary":"Competitive",
                                  "salaryBand":"UG3",
                                  "type":"Full-time",
                                  "closes":"31 Dec 2030",
                                  "closesAt":"2030-12-31",
                                  "visibility":"external",
                                  "requiredQualification":"Degree"%s
                                }
                                """.formatted(department)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();
        return dataId(result);
    }

    private long dataId(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("id").asLong();
    }

    private String adminToken(String email, String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (
                    email, password_hash, first_name, last_name,
                    account_type, admin_role, effective_type, email_verified
                ) VALUES (?, 'hash', 'System', 'Admin', 'admin', ?, 'admin', true)
                RETURNING id
                """, Long.class, email, role);
        return tokens.accessToken(new UserTokenDetails(
                id, email, "System", "Admin", "admin", role, null, "admin", 0));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
