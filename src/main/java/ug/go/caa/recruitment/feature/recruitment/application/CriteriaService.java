package ug.go.caa.recruitment.feature.recruitment.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CriteriaRepository;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CriteriaRepository.CriteriaData;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CriteriaRepository.CriteriaWrite;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class CriteriaService {

    private final CriteriaRepository repository;
    private final AuthorizationService authorization;
    private final AuditWriter audit;
    private final ObjectMapper mapper;

    public CriteriaService(
            CriteriaRepository repository,
            AuthorizationService authorization,
            AuditWriter audit,
            ObjectMapper mapper
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.mapper = mapper;
    }

    public PublicCriteria findPublic(long jobId) {
        return repository.findByJobId(jobId)
                .map(criteria -> new PublicCriteria(
                        publicRequirements(criteria.requirements()),
                        criteria.screeningQuestions()))
                .orElseGet(() -> new PublicCriteria(mapper.createArrayNode(), mapper.createArrayNode()));
    }

    public CriteriaData find(long jobId, AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canManageCriteria");
        return repository.findByJobId(jobId).orElse(null);
    }

    @Transactional
    public CriteriaData save(long jobId, AuthenticatedActor actor, CriteriaWrite criteria) {
        authorization.requirePermission(actor, "canManageCriteria");
        String title = repository.jobTitle(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Job not found"));
        CriteriaData saved = repository.save(jobId, criteria);
        audit.write(actor, "Updated criteria", title, criteriaSnapshot(saved));
        return saved;
    }

    private JsonNode criteriaSnapshot(CriteriaData criteria) {
        var node = mapper.createObjectNode();
        node.put("jobId", criteria.jobId());
        if (criteria.minCgpa() != null) node.put("minCgpa", criteria.minCgpa());
        if (criteria.minExperienceYears() != null) node.put("minExperienceYears", criteria.minExperienceYears());
        if (criteria.requiredQualLevel() != null) node.put("requiredQualLevel", criteria.requiredQualLevel());
        node.set("screeningQuestions", criteria.screeningQuestions());
        node.set("disqualifyingUniversities", criteria.disqualifyingUniversities());
        return node;
    }

    private JsonNode publicRequirements(JsonNode requirements) {
        ArrayNode filtered = mapper.createArrayNode();
        if (requirements != null && requirements.isArray()) {
            requirements.forEach(requirement -> {
                JsonNode usage = requirement.get("usage");
                if (usage == null || !"criteriaOnly".equals(usage.asText())) {
                    filtered.add(requirement);
                }
            });
        }
        return filtered;
    }

    public record PublicCriteria(JsonNode requirements, JsonNode screeningQuestions) {
    }
}
