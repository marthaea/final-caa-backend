package ug.go.caa.recruitment.feature.recruitment.web;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.recruitment.application.AssessmentService;
import ug.go.caa.recruitment.feature.recruitment.application.AssessmentService.AssessmentReport;
import ug.go.caa.recruitment.feature.recruitment.application.AssessmentService.AssessmentResponse;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/assessments")
public class AssessmentsController {

    private final AssessmentService assessments;

    public AssessmentsController(AssessmentService assessments) {
        this.assessments = assessments;
    }

    @GetMapping
    ApiResponse<List<AssessmentReport>> findAll(@AuthenticationPrincipal Jwt jwt) {
        List<AssessmentReport> result =
                assessments.findAll(AuthenticatedActor.from(jwt));
        return ApiResponse.list(result, result.size());
    }

    @GetMapping("/{applicationId}")
    ApiResponse<List<AssessmentResponse>> findByApplication(
            @PathVariable long applicationId, @AuthenticationPrincipal Jwt jwt
    ) {
        List<AssessmentResponse> result = assessments.findByApplication(
                applicationId, AuthenticatedActor.from(jwt));
        return ApiResponse.list(result, result.size());
    }

    @PutMapping("/{applicationId}/{type}")
    ApiResponse<AssessmentResponse> save(
            @PathVariable long applicationId,
            @PathVariable String type,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request
    ) {
        return ApiResponse.success(assessments.save(
                applicationId, type, AuthenticatedActor.from(jwt), request));
    }
}
