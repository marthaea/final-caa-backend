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
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
                        'application.intern-acceptance-requested'
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
        String to = text(payload, "to", text(payload, "email", null));
        String firstName = escape(text(payload, "firstName", "Applicant"));
        return switch (eventType) {
            case "identity.welcome-requested" -> new MailContent(
                    to,
                    "Welcome to CAA Recruitment",
                    "<p>Hello " + firstName + ",</p><p>Welcome to the CAA recruitment portal.</p>");
            case "identity.email-verification-requested" -> {
                String link = properties.frontendUrl() + "/verify-email?token=" + text(payload, "token", "");
                yield new MailContent(
                        to,
                        "Verify your email address",
                        "<p>Hello " + firstName + ",</p><p><a href=\"" + escape(link)
                                + "\">Verify your email address</a></p>");
            }
            case "identity.password-reset-requested" -> {
                String link = properties.frontendUrl() + "/reset-password?token=" + text(payload, "token", "");
                yield new MailContent(
                        to,
                        "Reset your password",
                        "<p>Hello " + firstName + ",</p><p><a href=\"" + escape(link)
                                + "\">Reset your password</a></p>");
            }
            case "email.custom-requested", "email.delivery-requested" -> new MailContent(
                    to, text(payload, "subject", ""), text(payload, "body", ""));
            // These two were previously enqueued by ApplicationService but never
            // reached here at all — excluded from the polling query above, so
            // every status-change and offer email silently sat undelivered forever.
            case "application.status-notification-requested" -> new MailContent(
                    to,
                    "Application Update — " + text(payload, "jobTitle", ""),
                    text(payload, "message", ""));
            case "application.intern-acceptance-requested" -> new MailContent(
                    to,
                    "Welcome to the CAA Internship Program — " + text(payload, "jobTitle", ""),
                    "<p>Dear " + firstName + ",</p><p>Congratulations on your offer for the position of "
                            + escape(text(payload, "jobTitle", "")) + " at the Uganda Civil Aviation Authority! "
                            + "We are excited to have you join the team.</p><p>A formal offer letter with your "
                            + "terms and conditions will follow separately. Please log in to the UCAA "
                            + "e-Recruitment Portal for further details.</p>");
            default -> throw new IllegalArgumentException("Unsupported email event " + eventType);
        };
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

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
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
