package ug.go.caa.recruitment.feature.support.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ug.go.caa.recruitment.feature.support.application.SupportService;
import ug.go.caa.recruitment.feature.support.application.SupportService.AnalyticsResponse;
import ug.go.caa.recruitment.feature.support.application.SupportService.SettingsCommand;
import ug.go.caa.recruitment.feature.support.application.SupportService.SettingsResponse;
import ug.go.caa.recruitment.feature.support.application.SupportService.TemplateCommand;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.AuditData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.ChatbotData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.EmailCommand;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.EmailData;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.NotificationData;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api")
public class SupportController {

    private final SupportService support;

    public SupportController(SupportService support) {
        this.support = support;
    }

    @GetMapping("/settings")
    ApiResponse<SettingsResponse> settings() {
        return ApiResponse.success(support.settings());
    }

    @PutMapping("/settings")
    ApiResponse<SettingsResponse> updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody SettingsRequest request
    ) {
        return ApiResponse.success(support.updateSettings(
                AuthenticatedActor.from(jwt), request.command()));
    }

    @GetMapping("/notifications")
    ApiResponse<List<NotificationData>> notifications(@AuthenticationPrincipal Jwt jwt) {
        List<NotificationData> result = support.notifications(AuthenticatedActor.from(jwt));
        return ApiResponse.list(result, result.size());
    }

    @PutMapping("/notifications/{id}/read")
    ApiResponse<Map<String, Object>> markNotificationRead(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(support.markNotificationRead(AuthenticatedActor.from(jwt), id));
    }

    @GetMapping("/emails")
    ApiResponse<List<EmailData>> emails(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String triggerEvent,
            @RequestParam(required = false) Integer limit
    ) {
        List<EmailData> result = support.emails(
                AuthenticatedActor.from(jwt), search, triggerEvent, limit);
        return ApiResponse.list(result, result.size());
    }

    @PostMapping("/emails")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<EmailData> sendEmail(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody EmailRequest request
    ) {
        return ApiResponse.success(support.sendEmail(
                AuthenticatedActor.from(jwt), request.command()));
    }

    @PostMapping("/emails/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Map<String, Integer>> sendBulkEmails(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody BulkEmailRequest request
    ) {
        List<EmailCommand> emails = request.emails() == null ? null
                : request.emails().stream().map(EmailRequest::command).toList();
        return ApiResponse.success(Map.of(
                "inserted", support.sendBulkEmails(AuthenticatedActor.from(jwt), emails)));
    }

    @DeleteMapping("/emails")
    ApiResponse<Map<String, String>> clearEmails(@AuthenticationPrincipal Jwt jwt) {
        support.clearEmails(AuthenticatedActor.from(jwt));
        return ApiResponse.success(Map.of("message", "Email log cleared"));
    }

    @GetMapping("/audit")
    ApiResponse<List<AuditData>> audits(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer limit
    ) {
        List<AuditData> result = support.audits(AuthenticatedActor.from(jwt), search, limit);
        return ApiResponse.list(result, result.size());
    }

    @PostMapping("/audit")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AuditData> createAudit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AuditRequest request
    ) {
        return ApiResponse.success(support.createAudit(
                AuthenticatedActor.from(jwt), request.action(), request.target()));
    }

    @PostMapping("/analytics/event")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Void> analyticsEvent(
            @RequestBody AnalyticsEventRequest request,
            @CookieValue(name = "caa_sid", required = false) String sessionId
    ) {
        support.recordAnalytics(
                request.type(), request.jobId(), request.jobTitle(), request.query(), sessionId);
        return ApiResponse.success(null);
    }

    @GetMapping("/analytics")
    ApiResponse<AnalyticsResponse> analytics(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer days
    ) {
        return ApiResponse.success(support.analytics(AuthenticatedActor.from(jwt), days));
    }

    @PostMapping("/chatbot/queries")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Map<String, Boolean>> recordChatbot(@RequestBody ChatbotRequest request) {
        support.recordChatbot(
                request.query(), request.matchedQuestion(), request.outcome(), request.persona());
        return ApiResponse.success(Map.of("logged", true));
    }

    @GetMapping("/chatbot/queries")
    ApiResponse<List<ChatbotData>> chatbot(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer limit
    ) {
        List<ChatbotData> result = support.chatbot(
                AuthenticatedActor.from(jwt), outcome, days, limit);
        return ApiResponse.list(result, result.size());
    }

    record SettingsRequest(
            String orgName,
            String emailSenderName,
            Integer minAgeThreshold,
            Boolean allowExternalInternalJobs,
            Integer sessionTimeoutMinutes,
            Integer closingSoonDays,
            Integer maxApplicationsPerCandidate,
            TemplateRequest notifTemplates
    ) {
        SettingsCommand command() {
            return new SettingsCommand(
                    orgName, emailSenderName, minAgeThreshold, allowExternalInternalJobs,
                    sessionTimeoutMinutes, closingSoonDays, maxApplicationsPerCandidate,
                    notifTemplates == null ? null : notifTemplates.command());
        }
    }

    record TemplateRequest(String shortlist, String decline, String interview, String offer) {
        TemplateCommand command() {
            return new TemplateCommand(shortlist, decline, interview, offer);
        }
    }

    record EmailRequest(
            String to,
            String candidateName,
            String subject,
            String body,
            String trigger,
            String jobTitle
    ) {
        EmailCommand command() {
            return new EmailCommand(to, candidateName, subject, body, trigger, jobTitle);
        }
    }

    record BulkEmailRequest(List<EmailRequest> emails) {
    }

    record AuditRequest(String action, String target) {
    }

    record AnalyticsEventRequest(String type, Long jobId, String jobTitle, String query) {
    }

    record ChatbotRequest(String query, String matchedQuestion, String outcome, String persona) {
    }

}
