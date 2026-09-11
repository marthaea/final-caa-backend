package ug.go.caa.recruitment.feature.recruitment.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.recruitment.application.JobTemplateService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.JobTemplateRepository.JobTemplate;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/job-templates")
public class JobTemplatesController {

    private final JobTemplateService templates;

    public JobTemplatesController(JobTemplateService templates) {
        this.templates = templates;
    }

    @GetMapping
    ApiResponse<List<JobTemplate>> findAll(@AuthenticationPrincipal Jwt jwt) {
        List<JobTemplate> result = templates.findAll(AuthenticatedActor.from(jwt));
        return ApiResponse.list(result, result.size());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<JobTemplate> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TemplateRequest request
    ) {
        return ApiResponse.success(templates.create(
                AuthenticatedActor.from(jwt), request.name(), request.departmentId(),
                request.sourceJobId(), request.content()));
    }

    @DeleteMapping("/{id}")
    ApiResponse<Map<String, String>> delete(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        templates.delete(id, AuthenticatedActor.from(jwt));
        return ApiResponse.success(Map.of("message", "Deleted"));
    }

    record TemplateRequest(String name, Long departmentId, Long sourceJobId, JsonNode content) {
    }
}
