package ug.go.caa.recruitment.feature.recruitment.application;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.JobTemplateRepository;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.JobTemplateRepository.JobTemplate;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class JobTemplateService {

    private final JobTemplateRepository repository;
    private final AuthorizationService authorization;
    private final AuditWriter audit;

    public JobTemplateService(
            JobTemplateRepository repository,
            AuthorizationService authorization,
            AuditWriter audit
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
    }

    public List<JobTemplate> findAll(AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canManageJobs");
        return repository.findAll();
    }

    @Transactional
    public JobTemplate create(
            AuthenticatedActor actor,
            String name,
            Long departmentId,
            Long sourceJobId,
            JsonNode content
    ) {
        authorization.requirePermission(actor, "canManageJobs");
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Template name is required");
        }
        if (content == null || !content.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Template content is required");
        }
        JobTemplate template = repository.create(
                name.trim(), departmentId, sourceJobId, content, actor.id());
        audit.write(actor, "Saved job template", name);
        return template;
    }

    @Transactional
    public void delete(long id, AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canManageJobs");
        JobTemplate template = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Template not found"));
        repository.delete(id);
        audit.write(actor, "Deleted job template", template.name());
    }
}
