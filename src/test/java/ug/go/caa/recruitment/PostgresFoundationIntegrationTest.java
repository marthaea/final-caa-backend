package ug.go.caa.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresFoundationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Test
    void contextBootsWithValidatedJpaAndPostgres() {
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void flywayBootstrapsEveryFoundationTable() {
        List<String> expected = List.of(
                "analytics_events", "api_rate_limits", "applications", "assessments", "audit_log",
                "candidate_scores", "chatbot_queries", "criteria", "cv_profiles",
                "departments", "job_templates", "jobs", "notifications",
                "outbox_events", "permission_overrides", "refresh_sessions", "sent_emails",
                "settings", "staff", "users");
        List<String> actual = jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                order by table_name
                """, String.class);

        assertThat(flyway.info().applied()).hasSize(2);
        assertThat(actual).containsAll(expected);
    }

    @Test
    void databaseEnforcesCaseInsensitiveEmailAndDomainChecks() {
        jdbc.update("""
                insert into users (email, password_hash, first_name, last_name)
                values ('constraint@example.org', 'hash', 'Test', 'User')
                """);

        assertThatThrownBy(() -> jdbc.update("""
                insert into users (email, password_hash, first_name, last_name)
                values ('CONSTRAINT@example.org', 'hash', 'Other', 'User')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                insert into users (email, password_hash, first_name, last_name, account_type)
                values ('invalid-type@example.org', 'hash', 'Invalid', 'Type', 'operator')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
