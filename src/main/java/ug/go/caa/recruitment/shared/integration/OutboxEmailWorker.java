package ug.go.caa.recruitment.shared.integration;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static ug.go.caa.recruitment.shared.integration.RecruitmentEmailTemplate.escape;
import static ug.go.caa.recruitment.shared.integration.RecruitmentEmailTemplate.greeting;
import static ug.go.caa.recruitment.shared.integration.RecruitmentEmailTemplate.heading;
import static ug.go.caa.recruitment.shared.integration.RecruitmentEmailTemplate.layout;
import static ug.go.caa.recruitment.shared.integration.RecruitmentEmailTemplate.mutedNote;
import static ug.go.caa.recruitment.shared.integration.RecruitmentEmailTemplate.paragraph;
import static ug.go.caa.recruitment.shared.integration.RecruitmentEmailTemplate.primaryButton;
import static ug.go.caa.recruitment.shared.integration.RecruitmentEmailTemplate.wrapCustomBody;

@Component
public class OutboxEmailWorker {

    private final JdbcClient jdbc;
    private final JavaMailSender mailSender;
    private final IntegrationProperties properties;
    private final ObjectMapper mapper;

    public OutboxEmailWorker(
            JdbcClient jdbc,
            JavaMailSender mailSender,
            IntegrationProperties properties,
            ObjectMapper mapper
    ) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${app.integrations.mail.poll-delay:5000}")
    @Transactional
    public void deliverNextBatch() {
        if (!properties.mail().enabled()) {
            return;
        }
        List<OutboxEvent> events = jdbc.sql("""
                SELECT id, event_type, payload::text
                FROM outbox_events
                WHERE published_at IS NULL AND available_at <= now()
                    AND event_type IN (
                        'identity.welcome-requested',
                        'identity.email-verification-requested',
                        'identity.password-reset-requested',
                        'email.custom-requested',
                        'email.delivery-requested',
                        'application.status-notification-requested',
                        'application.intern-acceptance-requested',
                        'job.submitted-for-review',
                        'job.pending-final-approval',
                        'job.declined'
                    )
                ORDER BY occurred_at
                FOR UPDATE SKIP LOCKED
                LIMIT 20
                """)
                .query(OutboxEvent.class)
                .list();
        events.forEach(this::deliver);
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    public void closeExpiredJobs() {
        jdbc.sql("""
                UPDATE jobs SET visibility = 'closed', updated_at = now()
                WHERE closes_at < current_date AND visibility <> 'closed'
                """).update();
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
    public void purgeOldAnalytics() {
        jdbc.sql("DELETE FROM analytics_events WHERE created_at < now() - interval '90 days'").update();
        jdbc.sql("DELETE FROM api_rate_limits WHERE window_start < now() - interval '1 day'").update();
    }

    private void deliver(OutboxEvent event) {
        try {
            JsonNode payload = mapper.readTree(event.payload());
            MailContent content = content(event.eventType(), payload);
            send(content);
            jdbc.sql("UPDATE outbox_events SET published_at = now(), attempts = attempts + 1 WHERE id = :id")
                    .param("id", event.id())
                    .update();
        } catch (Exception exception) {
            jdbc.sql("""
                    UPDATE outbox_events SET attempts = attempts + 1,
                        last_error = :error,
                        available_at = now() + interval '1 minute' * LEAST(60, attempts + 1)
                    WHERE id = :id
                    """)
                    .param("error", truncate(exception.getMessage(), 2000))
                    .param("id", event.id())
                    .update();
        }
    }

    private MailContent content(String eventType, JsonNode payload) {
        String to = recipient(payload);
        String firstName = firstNameRaw(payload);
        String portal = properties.frontendUrl();
        return switch (eventType) {
            case "identity.welcome-requested" -> new MailContent(
                    to,
                    "Welcome to UCAA e-Recruitment",
                    mailLayout(
                            "Welcome to the UCAA recruitment portal",
                            greeting(firstName)
                                    + paragraph(
                                            "Your account is ready. You can sign in to browse vacancies, "
                                                    + "build your candidate profile, and track applications in one place.")
                                    + primaryButton("Open recruitment portal", portal)));
            case "identity.email-verification-requested" -> {
                String link = portal + "/verify-email?token=" + text(payload, "token", "");
                yield new MailContent(
                        to,
                        "Verify your email address — UCAA Recruitment",
                        mailLayout(
                                "Confirm your email to receive application updates",
                                greeting(firstName)
                                        + paragraph(
                                                "Please confirm your email address so we can send you "
                                                        + "application updates and interview notifications.")
                                        + primaryButton("Verify email address", link)
                                        + mutedNote(
                                                "This link works once. If it expires, sign in to the portal "
                                                        + "and use Resend verification link.")));
            }
            case "identity.password-reset-requested" -> {
                String link = portal + "/reset-password?token=" + text(payload, "token", "");
                yield new MailContent(
                        to,
                        "Reset your password — UCAA Recruitment",
                        mailLayout(
                                "Reset your recruitment portal password",
                                greeting(firstName)
                                        + paragraph(
                                                "We received a request to reset your password. "
                                                        + "Use the button below to choose a new password.")
                                        + primaryButton("Reset password", link)
                                        + mutedNote(
                                                "If you did not request this, you can ignore this email. "
                                                        + "The link expires after one hour.")));
            }
            case "email.custom-requested", "email.delivery-requested" -> new MailContent(
                    to,
                    text(payload, "subject", "Message from UCAA HR"),
                    mailLayout(
                            text(payload, "subject", "Message from UCAA HR"),
                            wrapCustomBody(text(payload, "body", ""))));
            case "application.status-notification-requested" -> new MailContent(
                    to,
                    "Application update — " + text(payload, "jobTitle", "Vacancy"),
                    mailLayout(
                            "Update on your job application",
                            greeting(firstName)
                                    + heading(text(payload, "jobTitle", "Your application"))
                                    + wrapCustomBody(text(payload, "message", ""))
                                    + primaryButton("View your dashboard", portal + "/dashboard")));
            case "application.intern-acceptance-requested" -> new MailContent(
                    to,
                    "Internship offer — " + text(payload, "jobTitle", "UCAA"),
                    mailLayout(
                            "Congratulations on your internship offer",
                            greeting(firstName)
                                    + paragraph(
                                            "Congratulations on your offer for the position of <strong>"
                                                    + escape(text(payload, "jobTitle", ""))
                                                    + "</strong> at the Uganda Civil Aviation Authority. "
                                                    + "We are pleased to welcome you to the team.")
                                    + paragraph(
                                            "A formal offer letter with your terms and conditions will follow "
                                                    + "separately. Please sign in to the portal for next steps.")
                                    + primaryButton("Open recruitment portal", portal)));
            case "job.submitted-for-review" -> new MailContent(
                    to,
                    "Job submitted for review — " + text(payload, "jobTitle", ""),
                    mailLayout(
                            "Your job listing was submitted for department review",
                            paragraph(
                                    "Your listing <strong>" + escape(text(payload, "jobTitle", ""))
                                            + "</strong> has been submitted for department review by "
                                            + escape(text(payload, "submittedBy", "HR"))
                                            + ".")
                                    + mutedNote("You will receive another notification when review is complete.")));
            case "job.pending-final-approval" -> new MailContent(
                    to,
                    "Job awaiting final approval — " + text(payload, "jobTitle", ""),
                    mailLayout(
                            "Job listing passed department review",
                            paragraph(
                                    "Your listing <strong>" + escape(text(payload, "jobTitle", ""))
                                            + "</strong> passed department review and is awaiting final HR approval "
                                            + "(reviewed by " + escape(text(payload, "reviewedBy", "HOD")) + ").")));
            case "job.declined" -> new MailContent(
                    to,
                    "Job listing declined — " + text(payload, "jobTitle", ""),
                    mailLayout(
                            "Job listing was declined",
                            paragraph(
                                    "Your listing <strong>" + escape(text(payload, "jobTitle", ""))
                                            + "</strong> was declined at the "
                                            + escape(text(payload, "stage", "review"))
                                            + " stage.")
                                    + paragraph(
                                            "<strong>Reason:</strong> "
                                                    + escape(text(payload, "reason", "No reason provided.")))));
            default -> throw new IllegalArgumentException("Unsupported email event " + eventType);
        };
    }

    private String mailLayout(String preheader, String bodyHtml) {
        return layout(preheader, bodyHtml, emailLogoUrl());
    }

    private String emailLogoUrl() {
        String base = properties.frontendUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/caa-logo.png";
    }

    private String recipient(JsonNode payload) {
        String direct = text(payload, "to", text(payload, "email", null));
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        String creator = emailForUserId(payload, "creatorId");
        if (creator != null && !creator.isBlank()) {
            return creator;
        }
        return emailForJobCreator(payload);
    }

    private String firstNameRaw(JsonNode payload) {
        String explicit = text(payload, "firstName", null);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String full = text(payload, "candidateName", "Applicant");
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    private String emailForJobCreator(JsonNode payload) {
        JsonNode jobId = payload.get("jobId");
        if (jobId == null || jobId.isNull()) {
            return null;
        }
        return jdbc.sql("""
                SELECT u.email FROM jobs j
                JOIN users u ON u.id = j.created_by
                WHERE j.id = :jobId
                """)
                .param("jobId", jobId.asLong())
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private String emailForUserId(JsonNode payload, String field) {
        JsonNode id = payload.get(field);
        if (id == null || id.isNull()) {
            return null;
        }
        return jdbc.sql("SELECT email FROM users WHERE id = :id")
                .param("id", id.asLong())
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private void send(MailContent content) throws Exception {
        if (content.to() == null || content.to().isBlank()) {
            throw new IllegalArgumentException("Email recipient is missing");
        }
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        if (properties.mail().from() != null && !properties.mail().from().isBlank()) {
            helper.setFrom(properties.mail().from(), properties.mail().senderName());
        }
        helper.setTo(content.to());
        helper.setSubject(content.subject());
        helper.setText(content.html(), true);
        mailSender.send(message);
    }

    private static String text(JsonNode payload, String field, String fallback) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "Unknown mail delivery failure";
        }
        return value.substring(0, Math.min(max, value.length()));
    }

    public record OutboxEvent(long id, String eventType, String payload) {
    }

    private record MailContent(String to, String subject, String html) {
    }
}
