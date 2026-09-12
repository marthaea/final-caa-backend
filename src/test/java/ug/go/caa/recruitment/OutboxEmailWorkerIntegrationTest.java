package ug.go.caa.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ug.go.caa.recruitment.shared.integration.OutboxEmailWorker;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OutboxEmailWorkerIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.integrations.mail.enabled", () -> "true");
        registry.add("app.integrations.mail.from", () -> "noreply@example.org");
        registry.add("app.integrations.mail.sender-name", () -> "CAA Test");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "2525");
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxEmailWorker worker;

    @MockitoBean
    JavaMailSender mailSender;

    long creatorUserId;
    long jobId;

    @BeforeEach
    void seed() throws Exception {
        Session session = Session.getInstance(new Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(session));
        jdbc.execute("""
                TRUNCATE outbox_events, jobs, users RESTART IDENTITY CASCADE
                """);
        creatorUserId = jdbc.queryForObject("""
                INSERT INTO users (
                  email, password_hash, first_name, last_name,
                  account_type, admin_role, effective_type, email_verified
                ) VALUES (
                  'creator@example.org', 'hash', 'Job', 'Creator',
                  'admin', 'recruiter', 'admin', true
                ) RETURNING id
                """, Long.class);
        jobId = jdbc.queryForObject("""
                INSERT INTO jobs (
                  abbr, title, dept, dept_key, location, salary, salary_band, type,
                  closes, closes_at, visibility, min_age, required_experience,
                  required_qualification, description, featured, status, created_by
                ) VALUES (
                  'TST', 'Test Role', 'Ops', 'ops', 'Entebbe', 'UGX 1M', 'UG1', 'Full-time',
                  'Dec 31, 2026', '2026-12-31', 'external', 18, 0,
                  'Degree', 'Test job', false, 'draft', ?
                ) RETURNING id
                """, Long.class, creatorUserId);
    }

    @Test
    void deliversEveryRecruitmentEmailEventType() {
        List<EventSeed> events = List.of(
                new EventSeed("identity.welcome-requested",
                        """
                        {"email":"welcome@example.org","firstName":"Wel"}
                        """),
                new EventSeed("identity.email-verification-requested",
                        """
                        {"email":"verify@example.org","firstName":"Ver","token":"tok1"}
                        """),
                new EventSeed("identity.password-reset-requested",
                        """
                        {"email":"reset@example.org","firstName":"Res","token":"tok2"}
                        """),
                new EventSeed("email.delivery-requested",
                        """
                        {"to":"manual@example.org","subject":"Manual","body":"Body"}
                        """),
                new EventSeed("application.status-notification-requested",
                        """
                        {"to":"status@example.org","jobTitle":"Pilot","message":"Shortlisted"}
                        """),
                new EventSeed("application.intern-acceptance-requested",
                        """
                        {"to":"intern@example.org","candidateName":"Mary Intern","jobTitle":"Intern ATC"}
                        """),
                new EventSeed("job.submitted-for-review",
                        """
                        {"jobId":%d,"jobTitle":"Test Role","submittedBy":"Recruiter"}
                        """.formatted(jobId)),
                new EventSeed("job.pending-final-approval",
                        """
                        {"jobId":%d,"jobTitle":"Test Role","reviewedBy":"HOD"}
                        """.formatted(jobId)),
                new EventSeed("job.declined",
                        """
                        {"jobId":%d,"creatorId":%d,"jobTitle":"Test Role","stage":"review","reason":"Incomplete"}
                        """.formatted(jobId, creatorUserId))
        );

        for (EventSeed seed : events) {
            jdbc.update("""
                    INSERT INTO outbox_events (
                      event_id, aggregate_type, aggregate_id, event_type, payload
                    ) VALUES (?, 'test', '1', ?, ?::jsonb)
                    """, UUID.randomUUID(), seed.type(), seed.payload());
        }

        worker.deliverNextBatch();

        Integer pending = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Integer.class);
        assertThat(pending).isZero();
        verify(mailSender, atLeast(events.size())).send(any(jakarta.mail.internet.MimeMessage.class));
    }

    private record EventSeed(String type, String payload) {
    }
}
