package ug.go.caa.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ug.go.caa.recruitment.seed.DatabaseSeeder;
import ug.go.caa.recruitment.seed.SeedProperties;

@SpringBootTest(properties = "app.seed.mode=")
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DatabaseSeedIntegrationTest {

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

    @BeforeEach
    void clean() {
        jdbc.execute("""
                TRUNCATE applications, cv_profiles, jobs, staff, departments, settings,
                  analytics_events, users RESTART IDENTITY CASCADE
                """);
        jdbc.update("INSERT INTO settings DEFAULT VALUES");
    }

    @Test
    void coreAndDemoSeedAreIdempotentAndHashPasswords() {
        invoke("core", true, false);
        invoke("core", true, false);
        invoke("demo", true, false);
        invoke("demo", true, false);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM jobs WHERE job_ref LIKE 'caa-seed-%'", Integer.class))
                .isEqualTo(14);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM departments", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM applications WHERE candidate_email='jbukenya@gmail.com'",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT email_verified FROM users WHERE lower(email)=lower('admin@caa.go.ug')",
                Boolean.class)).isTrue();
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE lower(email)=lower('admin@caa.go.ug')",
                String.class);
        assertThat(hash).startsWith("$2");
        assertThat(new BCryptPasswordEncoder(12).matches("Admin@2026", hash)).isTrue();
    }

    private void invoke(String mode, boolean demo, boolean volume) {
        var props = new SeedProperties(mode, demo, volume, false);
        new DatabaseSeeder(
                JdbcClient.create(jdbc.getDataSource()),
                new BCryptPasswordEncoder(12),
                props).run();
    }
}
