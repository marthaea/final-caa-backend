package ug.go.caa.recruitment.feature.support.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class SupportRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate template;
    private final ObjectMapper mapper;

    public SupportRepository(JdbcClient jdbc, JdbcTemplate template, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.template = template;
        this.mapper = mapper;
    }

    public Optional<SettingsData> settings() {
        return jdbc.sql("SELECT * FROM settings ORDER BY id LIMIT 1")
                .query(this::mapSettings)
                .optional();
    }

    public SettingsData updateSettings(SettingsPatch patch) {
        long id = jdbc.sql("SELECT id FROM settings ORDER BY id LIMIT 1")
                .query(Long.class)
                .optional()
                .orElseGet(() -> jdbc.sql("INSERT INTO settings DEFAULT VALUES RETURNING id")
                        .query(Long.class).single());
        jdbc.sql("""
                UPDATE settings SET
                  org_name = COALESCE(:orgName, org_name),
                  email_sender_name = COALESCE(:sender, email_sender_name),
                  min_age_threshold = COALESCE(:minAge, min_age_threshold),
                  allow_external_internal_jobs = COALESCE(:externalJobs, allow_external_internal_jobs),
                  session_timeout_minutes = COALESCE(:timeout, session_timeout_minutes),
                  closing_soon_days = COALESCE(:closingDays, closing_soon_days),
                  max_applications_per_candidate = COALESCE(:maxApplications, max_applications_per_candidate),
                  notif_template_shortlist = COALESCE(:shortlist, notif_template_shortlist),
                  notif_template_decline = COALESCE(:decline, notif_template_decline),
                  notif_template_interview = COALESCE(:interview, notif_template_interview),
                  notif_template_offer = COALESCE(:offer, notif_template_offer),
                  notif_template_assessment_scheduled = COALESCE(:assessmentScheduled, notif_template_assessment_scheduled),
                  notif_template_panel_invite = COALESCE(:panelInvite, notif_template_panel_invite),
                  default_cgpa_threshold = COALESCE(:defaultCgpa, default_cgpa_threshold),
                  updated_at = now()
                WHERE id = :id
                """)
                .param("orgName", nonBlank(patch.orgName()))
                .param("sender", nonBlank(patch.emailSenderName()))
                .param("minAge", patch.minAgeThreshold())
                .param("externalJobs", patch.allowExternalInternalJobs())
                .param("timeout", patch.sessionTimeoutMinutes())
                .param("closingDays", patch.closingSoonDays())
                .param("maxApplications", patch.maxApplicationsPerCandidate())
                .param("shortlist", nonBlank(patch.shortlist()))
                .param("decline", nonBlank(patch.decline()))
                .param("interview", nonBlank(patch.interview()))
                .param("offer", nonBlank(patch.offer()))
                .param("assessmentScheduled", nonBlank(patch.assessmentScheduled()))
                .param("panelInvite", nonBlank(patch.panelInvite()))
                .param("defaultCgpa", patch.defaultCgpaThreshold())
                .param("id", id)
                .update();
        return settings().orElseThrow();
    }

    public List<NotificationData> notifications(String email) {
        return jdbc.sql("""
                SELECT * FROM notifications
                WHERE lower(recipient_email) = lower(:email)
                ORDER BY created_at DESC LIMIT 100
                """)
                .param("email", email)
                .query(this::mapNotification)
                .list();
    }

    public Optional<NotificationData> notification(long id) {
        return jdbc.sql("SELECT * FROM notifications WHERE id = :id")
                .param("id", id)
                .query(this::mapNotification)
                .optional();
    }

    public void markNotificationRead(long id) {
        jdbc.sql("UPDATE notifications SET is_read = true WHERE id = :id")
                .param("id", id).update();
    }

    public List<EmailData> emails(String search, String trigger, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sent_emails WHERE true");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (to_email ILIKE ? OR candidate_name ILIKE ? OR subject ILIKE ?)");
            String like = "%" + search + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (trigger != null && !trigger.isBlank()) {
            sql.append(" AND trigger_event = ?");
            params.add(trigger);
        }
        sql.append(" ORDER BY sent_at DESC LIMIT ?");
        params.add(limit);
        return template.query(sql.toString(), this::mapEmail, params.toArray());
    }

    public EmailData insertEmail(EmailCommand email) {
        long id = jdbc.sql("""
                INSERT INTO sent_emails
                  (to_email, candidate_name, subject, body, trigger_event, job_title)
                VALUES (:to, :name, :subject, :body, :trigger, :jobTitle)
                RETURNING id
                """)
                .param("to", email.to())
                .param("name", email.candidateName())
                .param("subject", email.subject())
                .param("body", email.body())
                .param("trigger", email.trigger())
                .param("jobTitle", email.jobTitle())
                .query(Long.class).single();
        return jdbc.sql("SELECT * FROM sent_emails WHERE id = :id")
                .param("id", id).query(this::mapEmail).single();
    }

    public void clearEmails() {
        jdbc.sql("DELETE FROM sent_emails").update();
    }

    public List<AuditData> audits(String search, int limit) {
        if (search == null || search.isBlank()) {
            return jdbc.sql("SELECT * FROM audit_log ORDER BY at DESC LIMIT :limit")
                    .param("limit", limit).query(this::mapAudit).list();
        }
        return jdbc.sql("""
                SELECT * FROM audit_log
                WHERE actor ILIKE :search OR action ILIKE :search OR target ILIKE :search
                ORDER BY at DESC LIMIT :limit
                """)
                .param("search", "%" + search + "%")
                .param("limit", limit)
                .query(this::mapAudit).list();
    }

    public AuditData insertAudit(
            long actorId, String actor, String role, String action, String target, JsonNode metadata
    ) {
        long id = jdbc.sql("""
                INSERT INTO audit_log (actor_user_id, actor, role, action, target, metadata)
                VALUES (:actorId, :actor, :role, :action, :target, CAST(:metadata AS jsonb)) RETURNING id
                """)
                .param("actorId", actorId).param("actor", actor).param("role", role)
                .param("action", action).param("target", target)
                .param("metadata", json(metadata))
                .query(Long.class).single();
        return jdbc.sql("SELECT * FROM audit_log WHERE id = :id")
                .param("id", id).query(this::mapAudit).single();
    }

    private static final List<String> MAIL_EVENT_TYPES = List.of(
            "identity.welcome-requested", "identity.email-verification-requested",
            "identity.password-reset-requested", "email.custom-requested",
            "email.delivery-requested", "application.status-notification-requested",
            "application.intern-acceptance-requested");

    public EmailStatusData emailStatus() {
        Integer pending = jdbc.sql("""
                SELECT count(*) FROM outbox_events
                WHERE published_at IS NULL AND event_type IN (:types)
                """).param("types", MAIL_EVENT_TYPES).query(Integer.class).single();
        Integer failing = jdbc.sql("""
                SELECT count(*) FROM outbox_events
                WHERE published_at IS NULL AND attempts > 0 AND event_type IN (:types)
                """).param("types", MAIL_EVENT_TYPES).query(Integer.class).single();
        OffsetDateTime lastSent = jdbc.sql("""
                SELECT max(published_at) FROM outbox_events
                WHERE published_at IS NOT NULL AND event_type IN (:types)
                """).param("types", MAIL_EVENT_TYPES).query(OffsetDateTime.class).optional().orElse(null);
        return new EmailStatusData(pending == null ? 0 : pending, failing == null ? 0 : failing,
                lastSent == null ? null : lastSent.toInstant());
    }

    public void insertAnalytics(String type, Long jobId, String jobTitle, String query, String sessionId) {
        jdbc.sql("""
                INSERT INTO analytics_events (event_type, job_id, job_title, query, session_id)
                VALUES (:type, :jobId, :jobTitle, :query, :sessionId)
                """)
                .param("type", type).param("jobId", jobId).param("jobTitle", jobTitle)
                .param("query", query).param("sessionId", sessionId).update();
    }

    public List<AnalyticsEventData> analyticsEvents(int days) {
        return jdbc.sql("""
                SELECT * FROM analytics_events
                WHERE created_at >= now() - (:days * interval '1 day')
                ORDER BY created_at DESC LIMIT 500
                """).param("days", days).query(this::mapAnalyticsEvent).list();
    }

    public List<EventCount> analyticsSummary() {
        return jdbc.sql("""
                SELECT event_type, count(*) AS count FROM analytics_events
                WHERE created_at >= now() - interval '7 days'
                GROUP BY event_type
                """).query((row, n) -> new EventCount(row.getString("event_type"), row.getLong("count"))).list();
    }

    public List<TopJob> topJobs(int days) {
        return jdbc.sql("""
                SELECT job_id, job_title, count(*) AS count FROM analytics_events
                WHERE event_type = 'job_view' AND created_at >= now() - (:days * interval '1 day')
                  AND job_id IS NOT NULL
                GROUP BY job_id, job_title ORDER BY count DESC LIMIT 5
                """).param("days", days).query((row, n) -> new TopJob(
                        row.getLong("job_id"), row.getString("job_title"), row.getLong("count"))).list();
    }

    public List<TopSearch> topSearches(int days) {
        return jdbc.sql("""
                SELECT query, count(*) AS count FROM analytics_events
                WHERE event_type = 'search' AND query IS NOT NULL
                  AND created_at >= now() - (:days * interval '1 day')
                GROUP BY query ORDER BY count DESC LIMIT 10
                """).param("days", days).query((row, n) -> new TopSearch(row.getString("query"), row.getLong("count"))).list();
    }

    public List<DailyCount> dailyCounts() {
        return jdbc.sql("""
                SELECT created_at::date AS date, count(*) AS count FROM analytics_events
                WHERE created_at >= now() - interval '7 days'
                GROUP BY created_at::date ORDER BY date
                """).query((row, n) -> new DailyCount(
                        row.getObject("date", LocalDate.class), row.getLong("count"))).list();
    }

    public void insertChatbot(String query, String matchedQuestion, String outcome, String persona, Integer confidence) {
        jdbc.sql("""
                INSERT INTO chatbot_queries (query, matched_question, outcome, persona, confidence)
                VALUES (:query, :matched, :outcome, :persona, :confidence)
                """).param("query", query).param("matched", matchedQuestion)
                .param("outcome", outcome).param("persona", persona)
                .param("confidence", confidence).update();
    }

    public List<ChatbotData> chatbot(String outcome, int days, int limit) {
        if (outcome == null) {
            return jdbc.sql("""
                    SELECT * FROM chatbot_queries
                    WHERE asked_at >= now() - (:days * interval '1 day')
                    ORDER BY asked_at DESC LIMIT :limit
                    """).param("days", days).param("limit", limit).query(this::mapChatbot).list();
        }
        return jdbc.sql("""
                SELECT * FROM chatbot_queries
                WHERE outcome = :outcome AND asked_at >= now() - (:days * interval '1 day')
                ORDER BY asked_at DESC LIMIT :limit
                """).param("outcome", outcome).param("days", days).param("limit", limit)
                .query(this::mapChatbot).list();
    }

    private SettingsData mapSettings(ResultSet row, int n) throws SQLException {
        return new SettingsData(
                row.getString("org_name"), row.getString("email_sender_name"),
                row.getInt("min_age_threshold"), row.getBoolean("allow_external_internal_jobs"),
                row.getInt("session_timeout_minutes"), row.getInt("closing_soon_days"),
                row.getInt("max_applications_per_candidate"),
                row.getString("notif_template_shortlist"), row.getString("notif_template_decline"),
                row.getString("notif_template_interview"), row.getString("notif_template_offer"),
                row.getBigDecimal("default_cgpa_threshold"),
                row.getString("notif_template_assessment_scheduled"),
                row.getString("notif_template_panel_invite"));
    }

    private NotificationData mapNotification(ResultSet row, int n) throws SQLException {
        return new NotificationData(row.getLong("id"), row.getString("recipient_email"),
                row.getString("title"), row.getString("message"), row.getBoolean("is_read"),
                row.getString("type"), instant(row, "created_at"));
    }

    private EmailData mapEmail(ResultSet row, int n) throws SQLException {
        return new EmailData(row.getLong("id"), row.getString("to_email"),
                row.getString("candidate_name"), row.getString("subject"), row.getString("body"),
                instant(row, "sent_at"), row.getString("trigger_event"), row.getString("job_title"));
    }

    private AuditData mapAudit(ResultSet row, int n) throws SQLException {
        return new AuditData(row.getLong("id"), instant(row, "at"), row.getString("actor"),
                row.getString("role"), row.getString("action"), row.getString("target"),
                jsonNode(row.getString("metadata")));
    }

    private JsonNode jsonNode(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid audit metadata JSON", exception);
        }
    }

    private String json(JsonNode value) {
        return value == null ? "{}" : value.toString();
    }

    private AnalyticsEventData mapAnalyticsEvent(ResultSet row, int n) throws SQLException {
        long jobId = row.getLong("job_id");
        Long nullableJobId = row.wasNull() ? null : jobId;
        return new AnalyticsEventData(row.getLong("id"), row.getString("event_type"),
                nullableJobId, row.getString("job_title"), row.getString("query"),
                instant(row, "created_at").toEpochMilli(), row.getString("session_id"));
    }

    private ChatbotData mapChatbot(ResultSet row, int n) throws SQLException {
        // wasNull() reflects only the most recently read column — reading it
        // after the other row.getX(...) calls below (each of which resets the
        // flag to their own result) would report whether asked_at was null,
        // not confidence, silently turning every real NULL confidence into 0.
        int confidenceValue = row.getInt("confidence");
        Integer confidence = row.wasNull() ? null : confidenceValue;
        return new ChatbotData(row.getLong("id"), row.getString("query"),
                row.getString("matched_question"), row.getString("outcome"),
                row.getString("persona"), instant(row, "asked_at"),
                confidence);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record SettingsPatch(String orgName, String emailSenderName, Integer minAgeThreshold,
            Boolean allowExternalInternalJobs, Integer sessionTimeoutMinutes, Integer closingSoonDays,
            Integer maxApplicationsPerCandidate, String shortlist, String decline,
            String interview, String offer, java.math.BigDecimal defaultCgpaThreshold,
            String assessmentScheduled, String panelInvite) {
    }

    public record SettingsData(String orgName, String emailSenderName, int minAgeThreshold,
            boolean allowExternalInternalJobs, int sessionTimeoutMinutes, int closingSoonDays,
            int maxApplicationsPerCandidate, String shortlist, String decline,
            String interview, String offer, java.math.BigDecimal defaultCgpaThreshold,
            String assessmentScheduled, String panelInvite) {
    }

    public record NotificationData(long id, String recipientEmail, String title, String message,
            boolean read, String type, Instant at) {
    }

    public record EmailCommand(String to, String candidateName, String subject, String body,
            String trigger, String jobTitle) {
    }

    public record EmailData(long id, String to, String candidateName, String subject, String body,
            Instant sentAt, String trigger, String jobTitle) {
    }

    public record AuditData(long id, Instant at, String actor, String role, String action, String target, JsonNode metadata) {
    }

    public record AnalyticsEventData(long id, String type, Long jobId, String jobTitle,
            String query, long ts, String sessionId) {
    }

    public record EventCount(String type, long count) {
    }

    public record TopJob(long jobId, String jobTitle, long count) {
    }

    public record TopSearch(String query, long count) {
    }

    public record DailyCount(LocalDate date, long count) {
    }

    public record ChatbotData(long id, String query, String matchedQuestion,
            String outcome, String persona, Instant askedAt, Integer confidence) {
    }

    public record EmailStatusData(int pending, int failing, Instant lastSentAt) {
    }
}
