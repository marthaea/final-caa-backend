package ug.go.caa.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AuthApiIntegrationTest {

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
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenService tokens;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE users, outbox_events, audit_log RESTART IDENTITY CASCADE");
    }

    @Test
    void registrationLoginAndAuthenticatedProfileRemainContractCompatible() throws Exception {
        MvcResult registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Candidate@Example.org",
                                  "password": "secret123",
                                  "firstName": "Ada",
                                  "lastName": "Lovelace",
                                  "accountType": "external"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("caa_refresh"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("candidate@example.org"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        String registrationToken = accessToken(registration);
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + registrationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Ada"))
                .andExpect(jsonPath("$.data.adminRole").doesNotExist());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events",
                Integer.class)).isEqualTo(2);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"CANDIDATE@example.org","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("caa_refresh"))
                .andExpect(jsonPath("$.data.effectiveType").value("external"));
    }

    @Test
    void refreshRotatesAndLogoutInvalidatesOutstandingRefreshTokens() throws Exception {
        MvcResult registration = register("session@example.org");
        Cookie originalRefresh = registration.getResponse().getCookie("caa_refresh");
        String accessToken = accessToken(registration);

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh-token").cookie(originalRefresh))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("caa_refresh"))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        mvc.perform(post("/api/auth/refresh-token").cookie(originalRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired refresh token"));

        mvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        Cookie rotatedRefresh = refreshed.getResponse().getCookie("caa_refresh");
        mvc.perform(post("/api/auth/refresh-token").cookie(rotatedRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Session expired. Please log in again."));
    }

    @Test
    void rejectsInvalidCredentialsAndProtectsAuthenticatedRoutes() throws Exception {
        register("protected@example.org");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"protected@example.org","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorised"));
    }

    @Test
    void roleDefaultsOverridesStaffAndAdminManagementEnforceRbac() throws Exception {
        String superToken = adminToken("super@example.org", "super");

        mvc.perform(get("/api/permissions/roles/defaults")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaults.super.canGrantPermissions").value(true))
                .andExpect(jsonPath("$.data.defaults.recruiter.canViewStaff").value(false));

        mvc.perform(post("/api/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeNumber":"CAA-100",
                                  "firstName":"Grace",
                                  "lastName":"Hopper",
                                  "dept":"IT",
                                  "joined":"2026-01-15"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.empNo").value("CAA-100"))
                .andExpect(jsonPath("$.data.status").value("Active"));

        mvc.perform(get("/api/staff/verify/CAA-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));

        mvc.perform(put("/api/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"target@example.org",
                                  "role":"recruiter",
                                  "canViewStaff":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canViewStaff").value(true))
                .andExpect(jsonPath("$.data.canShortlist").value(false));

        mvc.perform(post("/api/users/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"new-admin@example.org",
                                  "password":"admin123",
                                  "firstName":"New",
                                  "lastName":"Admin",
                                  "adminRole":"hr"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.adminRole").value("hr"))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    void candidateCannotCrossStaffPermissionBoundary() throws Exception {
        MvcResult registration = register("candidate-boundary@example.org");
        mvc.perform(get("/api/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(registration)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void forgotPasswordDoesNotSendEmailWhenAddressIsUnknown() throws Exception {
        jdbc.execute("TRUNCATE outbox_events RESTART IDENTITY CASCADE");
        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-in-system@example.org"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value(
                        "If that email is registered, a password reset link has been sent"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events
                WHERE event_type = 'identity.password-reset-requested'
                """, Integer.class)).isZero();
    }

    @Test
    void verificationAndPasswordResetTokensAreSinglePurposeAndInvalidateSessions() throws Exception {
        MvcResult registration = register("recovery@example.org");
        Cookie refresh = registration.getResponse().getCookie("caa_refresh");
        String verificationToken = jdbc.queryForObject(
                "SELECT verify_token FROM users WHERE email = 'recovery@example.org'",
                String.class);

        mvc.perform(get("/api/auth/verify-email").param("token", verificationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Email verified"));
        mvc.perform(get("/api/auth/verify-email").param("token", verificationToken))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"RECOVERY@example.org"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value(
                        "If that email is registered, a password reset link has been sent"));

        String resetToken = jdbc.queryForObject("""
                SELECT payload ->> 'token' FROM outbox_events
                WHERE event_type = 'identity.password-reset-requested'
                ORDER BY id DESC LIMIT 1
                """, String.class);
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"newPassword9"}
                                """.formatted(resetToken)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh-token").cookie(refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Session expired. Please log in again."));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"recovery@example.org","password":"newPassword9"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void internalRegistrationRequiresARealStaffNumber() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"internal@example.org",
                                  "password":"secret123",
                                  "firstName":"Internal",
                                  "lastName":"Candidate",
                                  "accountType":"internal",
                                  "employeeNumber":"MISSING"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Employee number not found"));

        jdbc.update("""
                INSERT INTO staff (employee_number, first_name, last_name)
                VALUES ('CAA-200', 'Internal', 'Candidate')
                """);
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"internal@example.org",
                                  "password":"secret123",
                                  "firstName":"Internal",
                                  "lastName":"Candidate",
                                  "accountType":"internal",
                                  "employeeNumber":"CAA-200"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountType").value("internal"))
                .andExpect(jsonPath("$.data.effectiveType").value("internal"));
    }

    private MvcResult register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "secret123",
                                  "firstName": "Test",
                                  "lastName": "User",
                                  "accountType": "external"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String accessToken(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data")
                .get("token")
                .asText();
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
}
