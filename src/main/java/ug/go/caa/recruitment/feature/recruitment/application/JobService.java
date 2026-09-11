package ug.go.caa.recruitment.feature.recruitment.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.JobRepository;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.JobRepository.JobData;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.JobRepository.JobWrite;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.persistence.OutboxWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class JobService {

    private static final List<String> SALARY_BANDS =
            List.of("UG1", "UG2", "UG3", "UG4", "UG5", "UG6", "UG7");
    private static final List<String> TYPES =
            List.of("Full-time", "Contract", "Fixed Term Contract");
    private static final List<String> QUALIFICATIONS =
            List.of("O-Level", "A-Level", "Certificate", "Diploma", "Degree", "Masters", "PhD");

    private final JobRepository repository;
    private final AuthorizationService authorization;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public JobService(
            JobRepository repository,
            AuthorizationService authorization,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    public List<JobResponse> findAll(AuthenticatedActor actor) {
        boolean admin = actor != null && actor.isAdmin();
        String effectiveType = actor == null ? "external" : actor.effectiveType();
        return repository.findVisible(admin, effectiveType).stream().map(this::response).toList();
    }

    public JobResponse findOne(long id, AuthenticatedActor actor) {
        JobData job = requireJob(id);
        boolean admin = actor != null && actor.isAdmin();
        String effectiveType = actor == null ? "external" : actor.effectiveType();
        if (!admin && !"published".equals(job.status())) {
            throw notFound();
        }
        if (!repository.allowsCrossVisibility()) {
            if (job.closesAt().isBefore(LocalDate.now())) {
                throw notFound();
            }
            if ("internal".equals(job.visibility())
                    && !"internal".equals(effectiveType)
                    && !"admin".equals(effectiveType)) {
                throw notFound();
            }
        }
        return response(job);
    }

    @Transactional
    public JobResponse create(AuthenticatedActor actor, JobCommand command) {
        authorization.requirePermission(actor, "canManageJobs");
        validate(command, true);
        JobData created = repository.create(write(command), actor.id());
        audit.write(actor, "Created job listing (draft)", created.title());
        return response(created);
    }

    @Transactional
    public JobResponse update(long id, AuthenticatedActor actor, JobCommand command) {
        authorization.requirePermission(actor, "canManageJobs");
        requireJob(id);
        validate(command, false);
        JobData updated = repository.update(id, write(command));
        audit.write(actor, "Updated job listing", updated.title());
        return response(updated);
    }

    @Transactional
    public JobResponse submitForReview(long id, AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canManageJobs");
        JobData job = requireJob(id);
        if (!"draft".equals(job.status()) && !"declined".equals(job.status())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Only a draft (or declined) job can be submitted for review");
        }
        repository.setWorkflow(id, "pending_review", null, null, null);
        audit.write(actor, "Submitted job for department review", job.title());
        outbox.write("job", id, "job.submitted-for-review", Map.of(
                "jobId", id, "jobTitle", job.title(), "submittedBy", actor.displayName()));
        return response(requireJob(id));
    }

    @Transactional
    public JobResponse review(long id, AuthenticatedActor actor, boolean approve, String reason) {
        authorization.requirePermission(actor, "canReviewJob");
        JobData job = requireJob(id);
        if (!"pending_review".equals(job.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Job is not awaiting department review");
        }
        if (!"super".equals(actor.adminRole())) {
            if (job.departmentId() == null) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "This job has no department assigned, so it cannot be routed for review");
            }
            if (!repository.isDepartmentHead(job.departmentId(), actor.id())) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "You are not the Head of Department for this job");
            }
        }
        if (approve) {
            repository.setWorkflow(id, "pending_approval", actor.id(), null, null);
            audit.write(actor, "Approved job at department review", job.title());
            outbox.write("job", id, "job.pending-final-approval", Map.of(
                    "jobId", id, "jobTitle", job.title(), "reviewedBy", actor.displayName()));
        } else {
            requireReason(reason);
            repository.setWorkflow(id, "draft", actor.id(), null, reason.trim());
            audit.write(actor, "Declined job at department review", job.title() + " — " + reason);
            declineEvent(job, actor, "department review", reason);
        }
        return response(requireJob(id));
    }

    @Transactional
    public JobResponse approve(long id, AuthenticatedActor actor, boolean approve, String reason) {
        authorization.requirePermission(actor, "canApproveJob");
        JobData job = requireJob(id);
        if (!"pending_approval".equals(job.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Job is not awaiting final approval");
        }
        if (approve) {
            repository.setWorkflow(id, "published", null, actor.id(), null);
            audit.write(actor, "Approved and published job listing", job.title());
        } else {
            requireReason(reason);
            repository.setWorkflow(id, "draft", null, actor.id(), reason.trim());
            audit.write(actor, "Declined job at final approval", job.title() + " — " + reason);
            declineEvent(job, actor, "final approval", reason);
        }
        return response(requireJob(id));
    }

    @Transactional
    public JobResponse publish(long id, AuthenticatedActor actor) {
        authorization.requireRole(actor, "super");
        JobData job = requireJob(id);
        repository.setWorkflow(id, "published", null, null, null);
        audit.write(actor, "Published job directly (Super Admin bypass)", job.title());
        return response(requireJob(id));
    }

    @Transactional
    public void delete(long id, AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canManageJobs");
        JobData job = requireJob(id);
        repository.delete(id);
        audit.write(actor, "Deleted job listing", job.title());
    }

    private void declineEvent(JobData job, AuthenticatedActor actor, String stage, String reason) {
        if (job.createdBy() != null) {
            outbox.write("job", job.id(), "job.declined", Map.of(
                    "jobId", job.id(), "creatorId", job.createdBy(), "jobTitle", job.title(),
                    "stage", stage, "reason", reason, "declinedBy", actor.displayName()));
        }
    }

    private void validate(JobCommand command, boolean create) {
        if (create && (blank(command.title()) || blank(command.dept()) || blank(command.deptKey())
                || blank(command.location()) || blank(command.salary()) || blank(command.salaryBand())
                || blank(command.type()) || blank(command.closes()) || command.closesAt() == null
                || blank(command.visibility()) || blank(command.requiredQualification()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing required fields");
        }
        if (command.salaryBand() != null && !SALARY_BANDS.contains(command.salaryBand())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "salaryBand must be one of UG1–UG7");
        }
        if (command.type() != null && !TYPES.contains(command.type())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "type must be one of: " + String.join(", ", TYPES));
        }
        if (command.requiredQualification() != null
                && !QUALIFICATIONS.contains(command.requiredQualification())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid requiredQualification");
        }
        if (command.minAge() != null && command.minAge() < 16) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "minAge must be an integer ≥ 16");
        }
        if (command.requiredExperience() != null && command.requiredExperience() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "requiredExperience must be ≥ 0");
        }
        if (command.vacancies() != null && command.vacancies() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "vacancies must be ≥ 1");
        }
    }

    private JobWrite write(JobCommand command) {
        return new JobWrite(
                command.title(), command.dept(), command.deptKey(), command.location(),
                command.salary(), command.salaryBand(), command.type(), command.closes(),
                command.closesAt(), command.visibility(), command.minAge(),
                command.requiredExperience(), command.requiredQualification(),
                command.description(), command.featured(), command.departmentId(),
                command.jobRef(), command.reportsTo(), command.vacancies(),
                command.aboutRole(), command.accountabilities(), command.specialSkills());
    }

    private JobResponse response(JobData job) {
        return new JobResponse(
                job.id(), job.abbr(), job.title(), job.dept(), job.deptKey(),
                job.location(), job.salary(), job.salaryBand(), job.type(), job.closes(),
                job.closesAt().toString(), job.visibility(), job.minAge(),
                job.requiredExperience(), job.requiredQualification(), job.description(),
                job.featured(), job.status(), job.departmentId(), job.declineReason(),
                job.jobRef(), job.reportsTo(), job.vacancies(), job.aboutRole(),
                job.accountabilities(), job.specialSkills());
    }

    private JobData requireJob(long id) {
        return repository.findById(id).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Job not found");
    }

    private void requireReason(String reason) {
        if (blank(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A reason is required to decline a job");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record JobCommand(
            String title,
            String dept,
            String deptKey,
            String location,
            String salary,
            String salaryBand,
            String type,
            String closes,
            LocalDate closesAt,
            String visibility,
            Integer minAge,
            Integer requiredExperience,
            String requiredQualification,
            String description,
            Boolean featured,
            Long departmentId,
            String jobRef,
            String reportsTo,
            Integer vacancies,
            String aboutRole,
            JsonNode accountabilities,
            JsonNode specialSkills
    ) {
    }

    public record JobResponse(
            long id,
            String abbr,
            String title,
            String dept,
            String deptKey,
            String location,
            String salary,
            String salaryBand,
            String type,
            String closes,
            String closesAt,
            String visibility,
            int minAge,
            int requiredExperience,
            String requiredQualification,
            String description,
            boolean featured,
            String status,
            Long departmentId,
            String declineReason,
            String jobRef,
            String reportsTo,
            int vacancies,
            String aboutRole,
            JsonNode accountabilities,
            JsonNode specialSkills
    ) {
    }
}
