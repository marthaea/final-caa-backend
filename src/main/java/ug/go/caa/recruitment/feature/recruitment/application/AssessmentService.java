package ug.go.caa.recruitment.feature.recruitment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.AssessmentRepository;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.AssessmentRepository.ApplicationSummary;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.AssessmentRepository.AssessmentData;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.AssessmentRepository.AssessmentWrite;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.persistence.OutboxWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class AssessmentService {

    private static final List<String> TYPES =
            List.of("written", "psychometric", "interview", "practical");
    // Scheduling an assessment can now be the direct next step straight from
    // either shortlisting stage — previously only "Interview" qualified,
    // which meant a candidate had to be manually advanced to Interview first
    // before the Assessment Schedule tab would even recognise them.
    private static final Set<String> ASSESSABLE_STATUSES =
            Set.of("Shortlisted", "Shortlisted II", "Interview");
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "written", "Written", "psychometric", "Psychometric",
            "interview", "Interview", "practical", "Practical");
    private static final DateTimeFormatter NOTIFY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu 'at' h:mm a", Locale.US);

    private static final String DEFAULT_SCHEDULED_TEMPLATE =
            "Dear {name},\n\nYour {type} assessment for the position of {role} at the Uganda Civil "
                    + "Aviation Authority has been scheduled for {when}.{venueLine}\n\nPlease log in to "
                    + "the UCAA e-Recruitment Portal for further details, and come prepared as "
                    + "instructed.\n\nYours sincerely,\nHuman Resources Department\n"
                    + "Uganda Civil Aviation Authority";

    private final AssessmentRepository repository;
    private final AuthorizationService authorization;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final SupportRepository supportRepository;

    public AssessmentService(
            AssessmentRepository repository,
            AuthorizationService authorization,
            AuditWriter audit,
            OutboxWriter outbox,
            SupportRepository supportRepository
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.supportRepository = supportRepository;
    }

    public List<AssessmentReport> findAll(AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canExport");
        return repository.findAll().stream().map(this::report).toList();
    }

    public List<AssessmentResponse> findByApplication(
            long applicationId, AuthenticatedActor actor
    ) {
        authorization.requirePermission(actor, "canViewApplications");
        return repository.findByApplication(applicationId).stream().map(this::response).toList();
    }

    @Transactional
    public AssessmentResponse save(
            long applicationId, String type, AuthenticatedActor actor, JsonNode request
    ) {
        if (!TYPES.contains(type)) {
            throw bad("type must be one of: " + String.join(", ", TYPES));
        }
        boolean canSchedule = authorization.hasPermission(actor, "canScheduleAssessment");
        boolean canRecord = authorization.hasPermission(actor, "canRecordAssessment");
        if (!canSchedule && !canRecord) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        var app = repository.application(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));
        boolean scheduleTouched = canSchedule
                && (request.has("scheduledAt") || request.has("venue"));
        boolean recordTouched = canRecord
                && (request.has("score") || request.has("passed") || request.has("notes"));
        if (!scheduleTouched && !recordTouched) {
            throw bad("Nothing to update — your role can only schedule or only record assessments");
        }

        AssessmentData current = repository.find(applicationId, type).orElse(null);
        OffsetDateTime scheduledAt = scheduleTouched
                ? timestamp(request.get("scheduledAt"))
                : current == null ? null : current.scheduledAt();
        String venue = scheduleTouched
                ? text(request.get("venue"))
                : current == null ? null : current.venue();
        Long scheduledBy = scheduleTouched
                ? actor.id() : current == null ? null : current.scheduledBy();

        BigDecimal score = current == null ? null : current.score();
        Boolean passed = current == null ? null : current.passed();
        String notes = current == null ? null : current.notes();
        Long recordedBy = current == null ? null : current.recordedBy();
        if (recordTouched) {
            score = decimal(request.get("score"));
            passed = bool(request.get("passed"));
            notes = text(request.get("notes"));
            if (score == null || passed == null) {
                throw bad("score and passed must both be provided when recording an assessment");
            }
            if (score.compareTo(BigDecimal.ZERO) < 0
                    || score.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw bad("score must be between 0 and 100");
            }
            recordedBy = actor.id();
        }

        AssessmentData saved = repository.save(new AssessmentWrite(
                applicationId, type, scheduledAt, venue, scheduledBy,
                score, passed, notes, recordedBy));
        if (scheduleTouched && ASSESSABLE_STATUSES.contains(app.status())) {
            repository.updateApplicationStatus(applicationId, "Assessment Scheduled");
        }
        if (recordTouched && repository.allScheduledRecorded(applicationId)
                && ("Assessment Scheduled".equals(app.status()) || "Interview".equals(app.status()))) {
            repository.updateApplicationStatus(applicationId, "Assessment Complete");
        }
        audit.write(actor, "Updated " + type + " assessment",
                app.candidateName() + " — " + app.title());
        if (scheduleTouched) {
            notifyScheduled(app, type, scheduledAt, venue);
        }
        return response(saved);
    }

    // Previously nothing notified the candidate when an assessment was
    // scheduled or rescheduled — HR had to remember to tell them manually.
    private void notifyScheduled(
            ApplicationSummary app, String type, OffsetDateTime scheduledAt, String venue
    ) {
        if (app.candidateEmail() == null || app.candidateEmail().isBlank() || scheduledAt == null) {
            return;
        }
        String typeLabel = TYPE_LABELS.getOrDefault(type, type);
        String when = NOTIFY_FORMAT.format(scheduledAt);
        String venueLine = venue != null && !venue.isBlank() ? " The venue is " + venue + "." : "";
        String template = supportRepository.settings()
                .map(SupportRepository.SettingsData::assessmentScheduled)
                .filter(t -> t != null && !t.isBlank())
                .orElse(DEFAULT_SCHEDULED_TEMPLATE);
        String body = template
                .replace("{name}", app.candidateName())
                .replace("{role}", app.title())
                .replace("{type}", typeLabel)
                .replace("{when}", when)
                .replace("{venueLine}", venueLine);
        outbox.write("assessments", app.id(), "email.delivery-requested", Map.of(
                "to", app.candidateEmail(),
                "candidateName", app.candidateName(),
                "subject", typeLabel + " Assessment Scheduled — " + app.title(),
                "body", body,
                "trigger", typeLabel + " Assessment Scheduled",
                "jobTitle", app.title()));
    }

    private AssessmentResponse response(AssessmentData data) {
        return new AssessmentResponse(
                data.id(), data.applicationId(), data.type(), data.scheduledAt(),
                data.venue(), data.score(), data.passed(), data.notes());
    }

    private AssessmentReport report(AssessmentData data) {
        return new AssessmentReport(
                data.id(), data.applicationId(), data.type(), data.scheduledAt(),
                data.venue(), data.score(), data.passed(), data.notes(),
                data.candidateName(), data.jobTitle(), data.dept());
    }

    private static OffsetDateTime timestamp(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.asText());
        } catch (DateTimeParseException exception) {
            throw bad("scheduledAt must be a valid date-time");
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            throw bad("score must be numeric");
        }
    }

    private static Boolean bool(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isBoolean()) {
            throw bad("passed must be boolean");
        }
        return node.asBoolean();
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public record AssessmentResponse(
            long id, long applicationId, String type, OffsetDateTime scheduledAt,
            String venue, BigDecimal score, Boolean passed, String notes
    ) {
    }

    public record AssessmentReport(
            long id, long applicationId, String type, OffsetDateTime scheduledAt,
            String venue, BigDecimal score, Boolean passed, String notes,
            String candidateName, String jobTitle, String dept
    ) {
    }
}
