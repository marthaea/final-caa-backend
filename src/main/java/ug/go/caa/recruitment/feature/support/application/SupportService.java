package ug.go.caa.recruitment.feature.support.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.AnalyticsEventData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.AuditData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.ChatbotData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.DailyCount;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.EmailCommand;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.EmailData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.EmailStatusData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.NotificationData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.SettingsData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.SettingsPatch;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.TopJob;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.TopSearch;
import ug.go.caa.recruitment.shared.integration.IntegrationProperties;
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
    private final IntegrationProperties integrations;

    public SupportService(
            SupportRepository repository,
            AuthorizationService authorization,
            AuditWriter audit,
            OutboxWriter outbox,
            IntegrationProperties integrations
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.integrations = integrations;
    }

    public SettingsResponse settings() {
        SettingsData data = repository.settings().orElse(new SettingsData(
                "Uganda Civil Aviation Authority", "CAA HR Team", 21, false,
                30, 7, 5, null, null, null, null, new java.math.BigDecimal("3.8"), null, null));
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
                templates == null ? null : templates.offer(),
                command.defaultCgpaThreshold(),
                templates == null ? null : templates.assessmentScheduled(),
                templates == null ? null : templates.panelInvite()));
        audit.write(actor, "Updated portal settings", null);
        return settingsResponse(updated);
    }

    // Settings → "Email delivery" indicator: SMTP_ENABLED was previously an
    // env var with no visibility from the admin UI at all, so HR had no way
    // to self-diagnose "why didn't this candidate get an email" without
    // asking a developer to check the .env file.
    public EmailStatusResponse emailStatus(AuthenticatedActor actor) {
        authorization.requireAnyPermission(actor, "canManageSettings", "canSendNotifications");
        EmailStatusData data = repository.emailStatus();
        return new EmailStatusResponse(integrations.mail().enabled(), data.pending(), data.failing(), data.lastSentAt());
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
        // canViewAudit alone would exclude hr_officer/hr/recruiter — the roles
        // that actually run shortlisting and need to see their own
        // accountability trail in Shortlisting Reports, not just auditors.
        authorization.requireAnyPermission(actor, "canViewAudit", "canShortlist");
        return repository.audits(search, cap(limit, 200, 1000));
    }

    @Transactional
    public AuditData createAudit(
            AuthenticatedActor actor,
            String action,
            String target,
            JsonNode metadata
    ) {
        // Previously a hardcoded role allowlist ("super","hr","recruiter") that
        // silently excluded hr_officer/dhra/hod — every role that can actually
        // run shortlisting (and so is the one writing these audit entries) —
        // meaning most shortlisting-run audit writes would have been rejected.
        authorization.requirePermission(actor, "canShortlist");
        if (blank(action)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "action is required");
        }
        return repository.insertAudit(actor.id(), actor.displayName(), actor.adminRole(), action, target, metadata);
    }

    public void recordAnalytics(
            String type,
            Long jobId,
            String jobTitle,
            String query,
            String sessionId
    ) {
        if (type == null || type.isBlank() || !ANALYTICS_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "type must be one of: page_view, job_view, apply_click, save_job, search");
        }
        repository.insertAnalytics(type, jobId, jobTitle, query, sessionId);
    }

    public AnalyticsResponse analytics(AuthenticatedActor actor, Integer days) {
        // canViewAudit alone would exclude hr_officer/hr/recruiter, same gap
        // already fixed for audits() above — those roles run recruitment day
        // to day and need to see site traffic, not just auditors.
        authorization.requireAnyPermission(actor, "canViewAudit", "canShortlist");
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
        // Top jobs/searches are shown to HR as a 30-day view (unlike the 7-day
        // summary cards and daily chart) — use the same window the caller asked
        // for instead of a second hardcoded 7 days, so the numbers match the label.
        List<TopJob> topJobs = repository.topJobs(requestedDays);
        List<TopSearch> topSearches = repository.topSearches(requestedDays);
        List<DailyCount> dailyCounts = repository.dailyCounts();
        return new AnalyticsResponse(events, summary, topJobs, topSearches, dailyCounts);
    }

    public void recordChatbot(
            String query,
            String matchedQuestion,
            String outcome,
            String persona,
            Integer confidence
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
                blank(persona) ? "guest" : truncate(persona, 20),
                confidence);
    }

    public List<ChatbotData> chatbot(
            AuthenticatedActor actor,
            String outcome,
            Integer days,
            Integer limit
    ) {
        // Same canViewAudit-only gap as analytics()/audits() above — Martha's
        // question log is shown inside the Site Analytics tab, so whoever can
        // see that page must be able to load this too.
        authorization.requireAnyPermission(actor, "canViewAudit", "canShortlist");
        // Set.of(...).contains(null) throws NPE rather than returning false —
        // and the caller's normal case (no outcome filter, "show everything")
        // is exactly the null case, so every unfiltered call to this endpoint
        // was a 500. The frontend's Martha panel never actually loaded.
        String filter = outcome != null && CHATBOT_OUTCOMES.contains(outcome) ? outcome : null;
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
                new TemplateResponse(data.shortlist(), data.decline(), data.interview(), data.offer(),
                        data.assessmentScheduled(), data.panelInvite()),
                data.defaultCgpaThreshold());
    }

    private static void validateSettings(SettingsCommand command) {
        if (command.minAgeThreshold() != null && command.minAgeThreshold() < 0
                || command.sessionTimeoutMinutes() != null && command.sessionTimeoutMinutes() <= 0
                || command.closingSoonDays() != null && command.closingSoonDays() < 0
                || command.maxApplicationsPerCandidate() != null
                        && command.maxApplicationsPerCandidate() <= 0
                || command.defaultCgpaThreshold() != null
                        && (command.defaultCgpaThreshold().signum() < 0
                                || command.defaultCgpaThreshold().compareTo(new java.math.BigDecimal("5")) > 0)) {
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
            Integer maxApplicationsPerCandidate, TemplateCommand notifTemplates,
            java.math.BigDecimal defaultCgpaThreshold) {
    }

    public record TemplateCommand(String shortlist, String decline, String interview, String offer,
            String assessmentScheduled, String panelInvite) {
    }

    public record SettingsResponse(String orgName, String emailSenderName, int minAgeThreshold,
            boolean allowExternalInternalJobs, int sessionTimeoutMinutes, int closingSoonDays,
            int maxApplicationsPerCandidate, TemplateResponse notifTemplates,
            java.math.BigDecimal defaultCgpaThreshold) {
    }

    public record TemplateResponse(String shortlist, String decline, String interview, String offer,
            String assessmentScheduled, String panelInvite) {
    }

    public record EmailStatusResponse(boolean enabled, int pending, int failing, java.time.Instant lastSentAt) {
    }

    public record AnalyticsSummary(long pageViews7, long jobViews7, long applyClicks7,
            long searches7, long saveJobs7) {
    }

    public record AnalyticsResponse(List<AnalyticsEventData> events, AnalyticsSummary summary,
            List<TopJob> topJobs, List<TopSearch> topSearches, List<DailyCount> dailyCounts) {
    }
}
