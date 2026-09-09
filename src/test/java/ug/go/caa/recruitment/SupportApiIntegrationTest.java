package ug.go.caa.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ug.go.caa.recruitment.shared.security.TokenService;
import ug.go.caa.recruitment.shared.security.TokenService.UserTokenDetails;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SupportApiIntegrationTest {

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

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE settings, notifications, sent_emails, audit_log,
                  analytics_events, chatbot_queries, outbox_events, permission_overrides,
                  users RESTART IDENTITY CASCADE
                """);
        jdbc.update("INSERT INTO settings DEFAULT VALUES");
    }

    @Test
    void settingsArePublicAndOnlySuperCanUpdateThemWithAudit() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orgName").value("Uganda Civil Aviation Authority"))
                .andExpect(jsonPath("$.data.notifTemplates").exists());

        String superToken = token("SUPER@EXAMPLE.ORG", "admin", "super");
        mvc.perform(put("/api/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgName":"CAA Uganda",
                                  "allowExternalInternalJobs":true,
                                  "closingSoonDays":3,
                                  "notifTemplates":{"shortlist":"Welcome {name}"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgName").value("CAA Uganda"))
                .andExpect(jsonPath("$.data.allowExternalInternalJobs").value(true))
                .andExpect(jsonPath("$.data.notifTemplates.shortlist").value("Welcome {name}"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'Updated portal settings'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void notificationReadsEnforceCaseInsensitiveOwnership() throws Exception {
        String owner = token("Owner@Example.org", "external", null);
        String other = token("other@example.org", "external", null);
        Long id = jdbc.queryForObject("""
                INSERT INTO notifications (recipient_email, title, message)
                VALUES ('OWNER@example.org', 'Update', 'Your application changed') RETURNING id
                """, Long.class);

        mvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].recipientEmail").value("OWNER@example.org"))
                .andExpect(jsonPath("$.data[0].read").value(false));

        mvc.perform(put("/api/notifications/{id}/read", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mvc.perform(put("/api/notifications/{id}/read", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.isRead").value(true));
    }

    @Test
    void emailHistoryPersistsAndDeliveryIsEnqueued() throws Exception {
        String superToken = token("mail-admin@example.org", "admin", "super");
        String email = """
                {
                  "to":"candidate@example.org",
                  "candidateName":"Candidate One",
                  "subject":"Application update",
                  "body":"Congratulations",
                  "trigger":"shortlisted",
                  "jobTitle":"Engineer"
                }
                """;

        mvc.perform(post("/api/emails")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON).content(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.to").value("candidate@example.org"))
                .andExpect(jsonPath("$.data.trigger").value("shortlisted"));

        mvc.perform(post("/api/emails/bulk")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emails\":[" + email + "," + email + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.inserted").value(2));

        mvc.perform(get("/api/emails")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .param("search", "CANDIDATE ONE")
                        .param("triggerEvent", "shortlisted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sent_emails", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events
                WHERE event_type = 'email.delivery-requested'
                """, Integer.class)).isEqualTo(3);

        mvc.perform(delete("/api/emails").header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Email log cleared"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sent_emails", Integer.class)).isZero();
    }

    @Test
    void auditAnalyticsAndChatbotKeepTheirContractShapesAndCaps() throws Exception {
        String superToken = token("analytics-admin@example.org", "admin", "super");

        mvc.perform(post("/api/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"Reviewed Report\",\"target\":\"Quarterly\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.actor").value("Test User"))
                .andExpect(jsonPath("$.data.role").value("super"));

        mvc.perform(get("/api/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .param("search", "reviewed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(post("/api/analytics/event")
                        .cookie(new Cookie("caa_sid", "session-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"job_view","jobTitle":"Engineer"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(post("/api/analytics/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"search\",\"query\":\"engineering\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/analytics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .param("days", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(2))
                .andExpect(jsonPath("$.data.summary.jobViews7").value(1))
                .andExpect(jsonPath("$.data.summary.searches7").value(1));

        mvc.perform(post("/api/chatbot/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query":"How do I apply?",
                                  "matchedQuestion":"Applications",
                                  "outcome":"answered"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.logged").value(true));

        mvc.perform(get("/api/chatbot/queries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken))
                        .param("outcome", "answered")
                        .param("limit", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].persona").value("guest"));
    }

    private String token(String email, String accountType, String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (
                    email, password_hash, first_name, last_name,
                    account_type, admin_role, effective_type, email_verified
                ) VALUES (?, 'hash', 'Test', 'User', ?, ?, ?, true)
                RETURNING id
                """, Long.class, email, accountType, role, accountType);
        return tokens.accessToken(new UserTokenDetails(
                id, email, "Test", "User", accountType, role, null, accountType, 0));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
