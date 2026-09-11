package ug.go.caa.recruitment.feature.recruitment.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.recruitment.application.ApplicationService;
import ug.go.caa.recruitment.feature.recruitment.application.ApplicationService.ApplicationResponse;
import ug.go.caa.recruitment.feature.recruitment.application.ApplicationService.StatusCommand;
import ug.go.caa.recruitment.feature.recruitment.application.ApplicationService.SubmitCommand;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.ApplicationRepository.StatusChange;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/applications")
public class ApplicationsController {

    private final ApplicationService applications;

    public ApplicationsController(ApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    ApiResponse<List<ApplicationResponse>> find(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        List<ApplicationResponse> result = applications.find(
                AuthenticatedActor.from(jwt), jobId, status, fromDate, toDate, email, limit, offset);
        return ApiResponse.list(result, result.size());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    ResponseEntity<String> export(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String email
    ) {
        String body = applications.export(
                AuthenticatedActor.from(jwt), jobId, status, fromDate, toDate, email);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"applications_" + System.currentTimeMillis() + ".csv\"")
                .body(body);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ApplicationResponse> submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ApplicationRequest request
    ) {
        return ApiResponse.success(applications.submit(
                AuthenticatedActor.from(jwt),
                new SubmitCommand(request.jobId(), request.cgpa(), request.university(),
                        request.screeningAnswers(), request.completion())));
    }

    @PutMapping("/bulk-status")
    ApiResponse<Map<String, Integer>> bulkStatus(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody BulkStatusRequest request
    ) {
        int updated = applications.bulkStatus(
                AuthenticatedActor.from(jwt), request == null ? null : request.updates());
        return ApiResponse.success(Map.of("updated", updated));
    }

    @PutMapping("/{id}/status")
    ApiResponse<ApplicationResponse> updateStatus(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody StatusRequest request
    ) {
        return ApiResponse.success(applications.updateStatus(
                id, AuthenticatedActor.from(jwt),
                new StatusCommand(request.status(), request.notifyEmail(), request.notifyMessage())));
    }

    @PutMapping("/{id}/deployment")
    ApiResponse<ApplicationResponse> deployment(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody DeploymentRequest request
    ) {
        return ApiResponse.success(applications.deployment(
                id, AuthenticatedActor.from(jwt),
                request.deploymentStation(), request.deploymentDate()));
    }

    @DeleteMapping("/{id}")
    ApiResponse<Map<String, String>> withdraw(
            @PathVariable long id, @AuthenticationPrincipal Jwt jwt
    ) {
        applications.withdraw(id, AuthenticatedActor.from(jwt));
        return ApiResponse.success(Map.of("message", "Withdrawn"));
    }

    record ApplicationRequest(
            Long jobId, BigDecimal cgpa, String university,
            JsonNode screeningAnswers, Integer completion
    ) {
    }

    record BulkStatusRequest(List<StatusChange> updates) {
    }

    record StatusRequest(String status, String notifyEmail, String notifyMessage) {
    }

    record DeploymentRequest(String deploymentStation, LocalDate deploymentDate) {
    }
}
