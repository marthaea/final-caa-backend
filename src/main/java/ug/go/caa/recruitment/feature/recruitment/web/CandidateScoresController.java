package ug.go.caa.recruitment.feature.recruitment.web;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ug.go.caa.recruitment.feature.recruitment.application.CandidateScoreService;
import ug.go.caa.recruitment.feature.recruitment.application.CandidateScoreService.CandidateScores;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CandidateScoreRepository.ScoreData;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/candidate-scores")
public class CandidateScoresController {

    private final CandidateScoreService scores;

    public CandidateScoresController(CandidateScoreService scores) {
        this.scores = scores;
    }

    @GetMapping
    ApiResponse<List<CandidateScores>> find(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String status
    ) {
        List<CandidateScores> result =
                scores.find(AuthenticatedActor.from(jwt), jobId, status);
        return ApiResponse.list(result, result.size());
    }

    @PutMapping("/{applicationId}")
    ApiResponse<ScoreData> save(
            @PathVariable long applicationId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ScoreRequest request
    ) {
        return ApiResponse.success(scores.save(
                applicationId, AuthenticatedActor.from(jwt), request.score(), request.comment()));
    }

    record ScoreRequest(BigDecimal score, String comment) {
    }
}
