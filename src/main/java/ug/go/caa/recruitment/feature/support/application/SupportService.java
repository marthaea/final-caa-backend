package ug.go.caa.recruitment.feature.support.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.AnalyticsEventData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.AuditData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.ChatbotData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.DailyCount;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.EmailCommand;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.EmailData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.NotificationData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.SettingsData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.SettingsPatch;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.TopJob;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.TopSearch;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.persistence.OutboxWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class SupportService {

    private static final Set<String> ANALYTICS_TYPES =
            Set.of("page_view", "job_view", "apply_click", "save_job", "search");
    private static final Set<String> CHATBOT_OUTCOMES =
            Set.of("answered", "suggested", "fallback");

    private final SupportRepository repository;
    private final AuthorizationService authorization;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public SupportService(
            SupportRepository repository,
            AuthorizationService authorization,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    public SettingsResponse settings() {
        SettingsData data = repository.settings().orElse(new SettingsData(
                "Uganda Civil Aviation Authority", "CAA HR Team", 21, false,
                30, 7, 5, null, null, null, null));
        return settingsResponse(data);
    }

    @Transactional
    public SettingsResponse updateSettings(AuthenticatedActor actor, SettingsCommand command) {
        authorization.requireRole(actor, "super");
        validateSettings(command);
        TemplateCommand templates = command.notifTemplates();
        SettingsData updated = repository.updateSettings(new SettingsPatch(
                command.orgName(), command.emailSenderName(), command.minAgeThreshold(),
                command.allowExternalInternalJobs(), command.sessionTimeoutMinutes(),
                command.closingSoonDays(), command.maxApplicationsPerCandidate(),
                templates == null ? null : templates.shortlist(),
                templates == null ? null : templates.decline(),
                templates == null ? null : templates.interview(),
                templates == null ? null : templates.offer()));
        audit.write(actor, "Updated portal settings", null);
        return settingsResponse(updated);
    }

    public List<NotificationData> notifications(AuthenticatedActor actor) {
        return repository.notifications(actor.email());
    }

    @Transactional
    public Map<String, Object> markNotificationRead(AuthenticatedActor actor, long id) {
        NotificationData notification = repository.notification(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.recipientEmail().equalsIgnoreCase(actor.email())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        repository.markNotificationRead(id);
        return Map.of("id", id, "isRead", true);
    }

    public List<EmailData> emails(AuthenticatedActor actor, String search, String trigger, Integer limit) {
        authorization.requirePermission(actor, "canViewApplications");
        return repository.emails(search, trigger, cap(limit, 500, 2000));
    }

    @Transactional
    public EmailData sendEmail(AuthenticatedActor actor, EmailCommand command) {
        authorization.requirePermission(actor, "canSendNotifications");
        validateEmail(command);
        EmailData email = repository.insertEmail(command);
        enqueue(email);
        return email;
    }

    @Transactional
    public int sendBulkEmails(AuthenticatedActor actor, List<EmailCommand> emails) {
        authorization.requirePermission(actor, "canSendNotifications");
        if (emails == null || emails.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "emails array is required and must not be empty");
        }
        for (EmailCommand command : emails) {
            validateEmail(command);
        }
        for (EmailCommand command : emails) {
            enqueue(repository.insertEmail(command));
        }
        return emails.size();
    }

    @Transactional
    public void clearEmails(AuthenticatedActor actor) {
        authorization.requireRole(actor, "super");
        repository.clearEmails();
    }

    public List<AuditData> audits(AuthenticatedActor actor, String search, Integer limit) {
        authorization.requirePermission(actor, "canViewAudit");
        return repository.audits(search, cap(limit, 200, 1000));
    }

    @Transactional
    public AuditData createAudit(
            AuthenticatedActor actor,
            String action,
            String target
    ) {
        authorization.requireRole(actor, "super", "hr", "recruiter");
        if (blank(action)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "action is required");
        }
        return repository.insertAudit(actor.id(), actor.displayName(), actor.adminRole(), action, target);
    }

    public void recordAnalytics(
            String type,
            Long jobId,
            String jobTitle,
            String query,
            String sessionId
    ) {
        if (!ANALYTICS_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "type must be one of: page_view, job_view, apply_click, save_job, search");
        }
        repository.insertAnalytics(type, jobId, jobTitle, query, sessionId);
    }

    public AnalyticsResponse analytics(AuthenticatedActor actor, Integer days) {
        authorization.requirePermission(actor, "canViewAudit");
        int requestedDays = cap(days, 30, 365);
        List<AnalyticsEventData> events = repository.analyticsEvents(requestedDays);
        Map<String, Long> counts = new HashMap<>();
        repository.analyticsSummary().forEach(item -> counts.put(item.type(), item.count()));
        AnalyticsSummary summary = new AnalyticsSummary(
                counts.getOrDefault("page_view", 0L),
                counts.getOrDefault("job_view", 0L),
                counts.getOrDefault("apply_click", 0L),
                counts.getOrDefault("search", 0L),
                counts.getOrDefault("save_job", 0L));
        List<TopJob> topJobs = repository.topJobs();
        List<TopSearch> topSearches = repository.topSearches();
        List<DailyCount> dailyCounts = repository.dailyCounts();
        return new AnalyticsResponse(events, summary, topJobs, topSearches, dailyCounts);
    }

    public void recordChatbot(
            String query,
            String matchedQuestion,
            String outcome,
            String persona
    ) {
        if (blank(query)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "query is required");
        }
        if (!CHATBOT_OUTCOMES.contains(outcome)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid outcome");
        }
        repository.insertChatbot(
                truncate(query, 500),
                blank(matchedQuestion) ? null : truncate(matchedQuestion, 255),
                outcome,
                blank(persona) ? "guest" : truncate(persona, 20));
    }

    public List<ChatbotData> chatbot(
            AuthenticatedActor actor,
            String outcome,
            Integer days,
            Integer limit
    ) {
        authorization.requirePermission(actor, "canViewAudit");
        String filter = CHATBOT_OUTCOMES.contains(outcome) ? outcome : null;
        return repository.chatbot(filter, cap(days, 30, 365), cap(limit, 200, 1000));
    }

    private void enqueue(EmailData email) {
        outbox.write("sent_email", email.id(), "email.delivery-requested", Map.of(
                "emailId", email.id(),
                "to", email.to(),
                "candidateName", email.candidateName(),
                "subject", email.subject(),
                "body", email.body(),
                "trigger", email.trigger(),
                "jobTitle", email.jobTitle()));
    }

    private static SettingsResponse settingsResponse(SettingsData data) {
        return new SettingsResponse(
                data.orgName(), data.emailSenderName(), data.minAgeThreshold(),
                data.allowExternalInternalJobs(), data.sessionTimeoutMinutes(),
                data.closingSoonDays(), data.maxApplicationsPerCandidate(),
                new TemplateResponse(data.shortlist(), data.decline(), data.interview(), data.offer()));
    }

    private static void validateSettings(SettingsCommand command) {
        if (command.minAgeThreshold() != null && command.minAgeThreshold() < 0
                || command.sessionTimeoutMinutes() != null && command.sessionTimeoutMinutes() <= 0
                || command.closingSoonDays() != null && command.closingSoonDays() < 0
                || command.maxApplicationsPerCandidate() != null
                        && command.maxApplicationsPerCandidate() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid settings values");
        }
    }

    private static void validateEmail(EmailCommand email) {
        if (email == null || blank(email.to()) || blank(email.candidateName())
                || blank(email.subject()) || blank(email.body()) || blank(email.trigger())
                || blank(email.jobTitle())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing required fields");
        }
    }

    private static int cap(Integer value, int defaultValue, int maximum) {
        return Math.min(value == null || value == 0 ? defaultValue : value, maximum);
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record SettingsCommand(String orgName, String emailSenderName, Integer minAgeThreshold,
            Boolean allowExternalInternalJobs, Integer sessionTimeoutMinutes, Integer closingSoonDays,
            Integer maxApplicationsPerCandidate, TemplateCommand notifTemplates) {
    }

    public record TemplateCommand(String shortlist, String decline, String interview, String offer) {
    }

    public record SettingsResponse(String orgName, String emailSenderName, int minAgeThreshold,
            boolean allowExternalInternalJobs, int sessionTimeoutMinutes, int closingSoonDays,
            int maxApplicationsPerCandidate, TemplateResponse notifTemplates) {
    }

    public record TemplateResponse(String shortlist, String decline, String interview, String offer) {
    }

    public record AnalyticsSummary(long pageViews7, long jobViews7, long applyClicks7,
            long searches7, long saveJobs7) {
    }

    public record AnalyticsResponse(List<AnalyticsEventData> events, AnalyticsSummary summary,
            List<TopJob> topJobs, List<TopSearch> topSearches, List<DailyCount> dailyCounts) {
    }
}
