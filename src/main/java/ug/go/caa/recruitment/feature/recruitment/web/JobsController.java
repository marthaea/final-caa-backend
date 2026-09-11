package ug.go.caa.recruitment.feature.recruitment.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.recruitment.application.JobService;
import ug.go.caa.recruitment.feature.recruitment.application.JobService.JobCommand;
import ug.go.caa.recruitment.feature.recruitment.application.JobService.JobResponse;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.security.TokenService;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/jobs")
public class JobsController {

    private final JobService jobs;
    private final TokenService tokens;

    public JobsController(JobService jobs, TokenService tokens) {
        this.jobs = jobs;
        this.tokens = tokens;
    }

    @GetMapping
    ApiResponse<List<JobResponse>> findAll(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        List<JobResponse> result = jobs.findAll(optionalActor(authorization));
        return ApiResponse.list(result, result.size());
    }

    @GetMapping("/{id}")
    ApiResponse<JobResponse> findOne(
            @PathVariable long id,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.success(jobs.findOne(id, optionalActor(authorization)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<JobResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JobRequest request
    ) {
        return ApiResponse.success(jobs.create(AuthenticatedActor.from(jwt), request.command()));
    }

    @PutMapping("/{id}")
    ApiResponse<JobResponse> update(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JobRequest request
    ) {
        return ApiResponse.success(jobs.update(id, AuthenticatedActor.from(jwt), request.command()));
    }

    @PutMapping("/{id}/submit-for-review")
    ApiResponse<JobResponse> submitForReview(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(jobs.submitForReview(id, AuthenticatedActor.from(jwt)));
    }

    @PutMapping("/{id}/review")
    ApiResponse<JobResponse> review(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody WorkflowRequest request
    ) {
        return ApiResponse.success(jobs.review(
                id, AuthenticatedActor.from(jwt), Boolean.TRUE.equals(request.approve()), request.reason()));
    }

    @PutMapping("/{id}/approve")
    ApiResponse<JobResponse> approve(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody WorkflowRequest request
    ) {
        return ApiResponse.success(jobs.approve(
                id, AuthenticatedActor.from(jwt), Boolean.TRUE.equals(request.approve()), request.reason()));
    }

    @PutMapping("/{id}/publish")
    ApiResponse<JobResponse> publish(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(jobs.publish(id, AuthenticatedActor.from(jwt)));
    }

    @DeleteMapping("/{id}")
    ApiResponse<Map<String, String>> delete(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        jobs.delete(id, AuthenticatedActor.from(jwt));
        return ApiResponse.success(Map.of("message", "Deleted"));
    }

    private AuthenticatedActor optionalActor(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            return AuthenticatedActor.from(
                    tokens.accessTokenDecoder().decode(authorization.substring(7)));
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }

    record WorkflowRequest(Boolean approve, String reason) {
    }

    record JobRequest(
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
        JobCommand command() {
            return new JobCommand(
                    title, dept, deptKey, location, salary, salaryBand, type,
                    closes, closesAt, visibility, minAge, requiredExperience,
                    requiredQualification, description, featured, departmentId,
                    jobRef, reportsTo, vacancies, aboutRole, accountabilities, specialSkills);
        }
    }
}
