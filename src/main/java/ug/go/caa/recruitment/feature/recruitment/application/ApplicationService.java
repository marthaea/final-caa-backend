package ug.go.caa.recruitment.feature.recruitment.application;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import ug.go.caa.recruitment.feature.recruitment.infrastructure.ApplicationRepository;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.ApplicationRepository.ApplicationData;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.ApplicationRepository.ScreeningCriteria;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.ApplicationRepository.StatusChange;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository.SettingsData;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.persistence.OutboxWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class ApplicationService {

    private static final List<String> STATUSES =
            List.of("Pending", "Under Review", "Shortlisted", "Shortlisted II", "Interview",
                    "Assessment Scheduled", "Assessment Complete", "Offered", "Declined");
    // Mirrors the frontend's NON_WITHDRAWABLE_STATUSES (AppContext.tsx) — once an
    // application reaches any of these, editing/resubmitting is a hard stop.
    private static final Set<String> EDIT_LOCKED_STATUSES = Set.of(
            "Shortlisted", "Shortlisted II", "Interview", "Assessment Scheduled",
            "Assessment Complete", "Offered");
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US);
    // Fallbacks for when no settings row exists yet — mirrors the seeded
    // defaults so behavior is identical either way.
    private static final Map<String, String> DEFAULT_TEMPLATES = Map.of(
            "Shortlisted", "Dear {name}, we are pleased to inform you that your application for {role} "
                    + "has been shortlisted. Our team will contact you with further instructions shortly.",
            "Declined", "Dear {name}, thank you for applying for {role}. After careful review, we regret "
                    + "to inform you that your application has not been successful at this stage.",
            "Interview", "Dear {name}, congratulations! Your application for {role} has progressed to the "
                    + "interview stage. Our HR team will contact you to confirm the date and time.",
            "Offered", "Dear {name}, we are delighted to offer you the position of {role}. Please review "
                    + "the attached offer letter and respond within five (5) working days.");

    private final ApplicationRepository repository;
    private final AuthorizationService authorization;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final SupportRepository supportRepository;

    public ApplicationService(
            ApplicationRepository repository,
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

    public List<ApplicationResponse> find(
            AuthenticatedActor actor, Long jobId, String status, LocalDate fromDate,
            LocalDate toDate, String email, Integer limit, Integer offset
    ) {
        if (!actor.isAdmin()) {
            return repository.findOwn(actor.email()).stream().map(this::response).toList();
        }
        authorization.requirePermission(actor, "canViewApplications");
        return repository.find(jobId, status, fromDate, toDate, email, limit, offset)
                .stream().map(this::response).toList();
    }

    public String export(
            AuthenticatedActor actor, Long jobId, String status,
            LocalDate fromDate, LocalDate toDate, String email
    ) {
        authorization.requirePermission(actor, "canViewApplications");
        StringBuilder csv = new StringBuilder(
                "ID,Name,Email,Abbr,Job Title,Department,Date,Status,Completion%,CGPA,University");
        for (ApplicationData app : repository.export(jobId, status, fromDate, toDate, email)) {
            csv.append("\r\n").append(app.id()).append(',')
                    .append(escape(app.candidateName())).append(',')
                    .append(escape(app.candidateEmail())).append(',')
                    .append(escape(app.abbr())).append(',')
                    .append(escape(app.title())).append(',')
                    .append(escape(app.dept())).append(',')
                    .append(escape(app.date())).append(',')
                    .append(escape(app.status())).append(',')
                    .append(app.completion()).append(',')
                    .append(app.cgpa() == null ? "" : app.cgpa().toPlainString()).append(',')
                    .append(escape(app.university()));
        }
        return csv.toString();
    }

    @Transactional
    public ApplicationResponse submit(AuthenticatedActor actor, SubmitCommand command) {
        if (command.jobId() == null || command.jobId() < 1) {
            throw bad("Valid jobId required");
        }
        if (command.completion() == null || command.completion() < 0 || command.completion() > 100) {
            throw bad("completion must be between 0 and 100");
        }
        if (command.cgpa() != null
                && (command.cgpa().compareTo(BigDecimal.ZERO) < 0
                || command.cgpa().compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw bad("cgpa must be between 0.0 and 5.0");
        }
        if (command.university() != null && command.university().length() > 255) {
            throw bad("university must be at most 255 characters");
        }
        if (command.screeningAnswers() != null && !command.screeningAnswers().isObject()) {
            throw bad("screeningAnswers must be an object");
        }
        var job = repository.acceptingJob(command.jobId()).orElse(null);
        if (job == null) {
            if (!repository.jobExists(command.jobId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Job not found");
            }
            throw bad("This vacancy is no longer accepting applications");
        }
        // Previously any existing row (any status) blocked resubmission outright,
        // and the frontend's silent .catch() on that 409 meant a candidate editing
        // an already-submitted application saw a false "success" toast while
        // nothing was actually saved. Now: still-editable applications are
        // updated in place; anything at Shortlisted or later is a real, clearly
        // messaged hard stop instead of a silently-swallowed error.
        var existing = repository.findByJobAndEmail(command.jobId(), actor.email());
        if (existing.isPresent()) {
            String currentStatus = existing.get().status();
            if (EDIT_LOCKED_STATUSES.contains(currentStatus)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Your application has already progressed to " + currentStatus
                                + " and can no longer be edited. Contact HR if you need to make a change.");
            }
            boolean stillEligible = repository.criteria(command.jobId())
                    .map(criteria -> !fails(criteria, command)).orElse(true);
            String nextStatus = stillEligible ? currentStatus : "Declined";
            return response(repository.updateSubmission(
                    existing.get().id(), command.completion(), command.cgpa(),
                    emptyToNull(command.university()), command.screeningAnswers(), nextStatus));
        }
        int maximum = repository.applicationLimit();
        if (maximum > 0 && repository.activeCount(actor.email()) >= maximum) {
            throw bad("Application limit reached. You may not have more than "
                    + maximum + " active applications.");
        }
        String status = repository.criteria(command.jobId())
                .filter(criteria -> fails(criteria, command))
                .map(ignored -> "Declined")
                .orElse("Pending");
        return response(repository.create(
                job, actor.id(), actor.email(), actor.displayName(),
                DISPLAY_DATE.format(LocalDate.now()), status, command.completion(),
                command.cgpa(), emptyToNull(command.university()), command.screeningAnswers()));
    }

    @Transactional
    public int bulkStatus(AuthenticatedActor actor, List<StatusChange> updates) {
        authorization.requirePermission(actor, "canShortlist");
        if (updates == null || updates.isEmpty()) {
            throw bad("updates array is required and must not be empty");
        }
        updates.forEach(update -> {
            if (update.id() < 1 || !STATUSES.contains(update.status())) {
                throw bad("Each update must have a valid id and status");
            }
        });
        // Previously this only updated the `status` column — no email, no
        // in-app notification, for any candidate, ever. Bulk is the primary
        // path real shortlisting runs go through (hundreds of candidates at
        // once), so this was the single biggest gap in "every decision
        // notifies the candidate."
        for (StatusChange update : updates) {
            repository.updateStatus(update.id(), update.status());
            repository.findById(update.id()).ifPresent(app -> {
                notifyAutomatically(app, update.status());
                if ("Offered".equals(update.status()) && app.cgpa() != null) {
                    outbox.write("application", app.id(), "application.intern-acceptance-requested", Map.of(
                            "applicationId", app.id(), "to", app.candidateEmail(),
                            "candidateName", app.candidateName(), "jobTitle", app.title()));
                }
            });
        }
        audit.write(actor, "Bulk status update (" + updates.size() + " applications)", null);
        return updates.size();
    }

    @Transactional
    public ApplicationResponse updateStatus(
            long id, AuthenticatedActor actor, StatusCommand command
    ) {
        authorization.requirePermission(actor, "canShortlist");
        if (command.status() == null || !STATUSES.contains(command.status())) {
            throw bad("status must be one of: " + String.join(", ", STATUSES));
        }
        if (command.notifyMessage() != null && command.notifyMessage().length() > 2000) {
            throw bad("notifyMessage must be at most 2000 characters");
        }
        if (notBlank(command.notifyEmail())
                && !command.notifyEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw bad("notifyEmail must be a valid email address");
        }
        ApplicationData app = require(id);
        repository.updateStatus(id, command.status());
        if (notBlank(command.notifyEmail()) && notBlank(command.notifyMessage())) {
            String type = switch (command.status()) {
                case "Shortlisted" -> "shortlisted";
                case "Declined" -> "declined";
                case "Interview" -> "interview";
                case "Offered" -> "offered";
                default -> "info";
            };
            repository.addNotification(
                    app.candidateEmail().equalsIgnoreCase(command.notifyEmail())
                            ? app.candidateUserId() : null,
                    command.notifyEmail(),
                    "Application status: " + command.status(), command.notifyMessage(), type);
            outbox.write("application", id, "application.status-notification-requested", Map.of(
                    "applicationId", id, "to", command.notifyEmail(),
                    "candidateName", app.candidateName(), "jobTitle", app.title(),
                    "status", command.status(), "message", command.notifyMessage()));
        } else {
            // No custom message supplied (e.g. a drag on the pipeline board) —
            // still guarantee the candidate is notified, from the HR-editable
            // template for this status.
            notifyAutomatically(app, command.status());
        }
        if ("Offered".equals(command.status()) && app.cgpa() != null) {
            outbox.write("application", id, "application.intern-acceptance-requested", Map.of(
                    "applicationId", id, "to", app.candidateEmail(),
                    "candidateName", app.candidateName(), "jobTitle", app.title()));
        }
        audit.write(actor, "Updated application status to " + command.status(),
                app.candidateName() + " (" + app.abbr() + ")");
        return response(require(id));
    }

    @Transactional
    public ApplicationResponse deployment(
            long id, AuthenticatedActor actor, String station, LocalDate date
    ) {
        authorization.requirePermission(actor, "canShortlist");
        ApplicationData app = require(id);
        boolean stationPresent = notBlank(station);
        if (stationPresent != (date != null)) {
            throw bad("deploymentStation and deploymentDate must both be provided or both be null");
        }
        repository.updateDeployment(id, stationPresent ? station : null, date);
        audit.write(actor, "Recorded candidate deployment",
                app.candidateName() + " (" + app.abbr() + ")");
        return response(require(id));
    }

    @Transactional
    public void withdraw(long id, AuthenticatedActor actor) {
        ApplicationData app = repository.findOwned(id, actor.email())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));
        if (EDIT_LOCKED_STATUSES.contains(app.status())) {
            throw bad("Cannot withdraw an application at this stage");
        }
        repository.delete(id);
    }

    private boolean fails(ScreeningCriteria criteria, SubmitCommand command) {
        if (criteria.minCgpa() != null && command.cgpa() != null
                && command.cgpa().compareTo(criteria.minCgpa()) < 0) {
            return true;
        }
        if (notBlank(command.university()) && criteria.disqualifyingUniversities() != null) {
            for (JsonNode university : criteria.disqualifyingUniversities()) {
                if (command.university().equalsIgnoreCase(university.asText())) {
                    return true;
                }
            }
        }
        if (command.screeningAnswers() != null && criteria.screeningQuestions() != null) {
            for (JsonNode question : criteria.screeningQuestions()) {
                JsonNode kind = question.get("kind");
                if (kind == null || kind.asText().isBlank()) {
                    continue;
                }
                JsonNode answer = command.screeningAnswers().get(question.path("id").asText());
                if (!answerPasses(question, answer)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean answerPasses(JsonNode question, JsonNode answer) {
        if ("yesno".equals(question.path("kind").asText())) {
            String qualifying = question.hasNonNull("qualifyingAnswer")
                    ? question.get("qualifyingAnswer").asText() : "Yes";
            return answer != null && qualifying.equals(answer.asText());
        }
        if ("number".equals(question.path("kind").asText())) {
            if (answer == null) {
                return false;
            }
            try {
                double value = Double.parseDouble(answer.asText());
                return (!question.hasNonNull("min") || value >= question.get("min").asDouble())
                        && (!question.hasNonNull("max") || value <= question.get("max").asDouble());
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    // Guaranteed notification path — fires for every notifiable status change
    // regardless of whether HR supplied a custom message (bulk actions never
    // do). Uses the HR-editable templates in Settings, falling back to the
    // seeded defaults if no settings row exists.
    private void notifyAutomatically(ApplicationData app, String status) {
        String template = templateFor(status);
        if (template == null || app.candidateEmail() == null || app.candidateEmail().isBlank()) {
            return;
        }
        String message = template.replace("{name}", app.candidateName()).replace("{role}", app.title());
        String notifType = switch (status) {
            case "Shortlisted" -> "shortlisted";
            case "Declined" -> "declined";
            case "Interview" -> "interview";
            case "Offered" -> "offered";
            default -> "info";
        };
        repository.addNotification(app.candidateUserId(), app.candidateEmail(),
                "Application status: " + status, message, notifType);
        outbox.write("application", app.id(), "email.delivery-requested", Map.of(
                "to", app.candidateEmail(), "candidateName", app.candidateName(),
                "subject", "Application Update — " + app.title(), "body", message,
                "trigger", status, "jobTitle", app.title()));
    }

    private String templateFor(String status) {
        SettingsData settings = supportRepository.settings().orElse(null);
        return switch (status) {
            case "Shortlisted" -> settings != null ? settings.shortlist() : DEFAULT_TEMPLATES.get(status);
            case "Declined" -> settings != null ? settings.decline() : DEFAULT_TEMPLATES.get(status);
            case "Interview" -> settings != null ? settings.interview() : DEFAULT_TEMPLATES.get(status);
            case "Offered" -> settings != null ? settings.offer() : DEFAULT_TEMPLATES.get(status);
            default -> null;
        };
    }

    private ApplicationData require(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private ApplicationResponse response(ApplicationData app) {
        return new ApplicationResponse(
                app.id(), app.jobId(), app.abbr(), app.title(), app.dept(), app.date(),
                app.status(), app.completion(), app.candidateName(), app.candidateEmail(),
                app.cgpa(), app.university(), app.screeningAnswers(),
                app.deploymentStation(), app.deploymentDate());
    }

    private static String escape(Object value) {
        return value == null ? "" : "\"" + value.toString().replace("\"", "\"\"") + "\"";
    }

    private static String emptyToNull(String value) {
        return notBlank(value) ? value : null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public record SubmitCommand(
            Long jobId, BigDecimal cgpa, String university,
            JsonNode screeningAnswers, Integer completion
    ) {
    }

    public record StatusCommand(String status, String notifyEmail, String notifyMessage) {
    }

    public record ApplicationResponse(
            long id, long jobId, String abbr, String title, String dept, String date,
            String status, int completion, String candidateName, String candidateEmail,
            BigDecimal cgpa, String university, JsonNode screeningAnswers,
            String deploymentStation, LocalDate deploymentDate
    ) {
    }
}
