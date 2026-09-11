package ug.go.caa.recruitment.feature.recruitment.web;

import java.math.BigDecimal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.recruitment.application.CriteriaService;
import ug.go.caa.recruitment.feature.recruitment.application.CriteriaService.PublicCriteria;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CriteriaRepository.CriteriaData;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CriteriaRepository.CriteriaWrite;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/criteria")
public class CriteriaController {

    private final CriteriaService criteria;

    public CriteriaController(CriteriaService criteria) {
        this.criteria = criteria;
    }

    @GetMapping("/{jobId}/public")
    ApiResponse<PublicCriteria> findPublic(@PathVariable long jobId) {
        return ApiResponse.success(criteria.findPublic(jobId));
    }

    @GetMapping("/{jobId}")
    ApiResponse<CriteriaData> find(
            @PathVariable long jobId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(criteria.find(jobId, AuthenticatedActor.from(jwt)));
    }

    @PutMapping("/{jobId}")
    ApiResponse<CriteriaData> save(
            @PathVariable long jobId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CriteriaRequest request
    ) {
        return ApiResponse.success(criteria.save(
                jobId,
                AuthenticatedActor.from(jwt),
                new CriteriaWrite(
                        request.minCgpa(), request.minExperienceYears(),
                        request.requiredQualLevel(), request.requiredKeywords(),
                        request.disqualifyingUniversities(), request.screeningQuestions(),
                        request.assessmentTypes(), request.requirements(), request.notes())));
    }

    record CriteriaRequest(
            BigDecimal minCgpa,
            Integer minExperienceYears,
            String requiredQualLevel,
            JsonNode requiredKeywords,
            JsonNode disqualifyingUniversities,
            JsonNode screeningQuestions,
            JsonNode assessmentTypes,
            JsonNode requirements,
            String notes
    ) {
    }
}
