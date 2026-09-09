package ug.go.caa.recruitment.feature.recruitment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CandidateScoreRepository;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CandidateScoreRepository.ScoreData;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class CandidateScoreService {

    private final CandidateScoreRepository repository;
    private final AuthorizationService authorization;
    private final AuditWriter audit;

    public CandidateScoreService(
            CandidateScoreRepository repository,
            AuthorizationService authorization,
            AuditWriter audit
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
    }

    public List<CandidateScores> find(
            AuthenticatedActor actor, Long jobId, String status
    ) {
        authorization.requirePermission(actor, "canShortlist");
        if (jobId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "jobId is required");
        }
        Map<Long, CandidateScoresBuilder> grouped = new LinkedHashMap<>();
        repository.applications(jobId, status).forEach(app ->
                grouped.put(app.id(), new CandidateScoresBuilder(
                        app.id(), app.candidateName(), app.candidateEmail(), app.status())));
        repository.scores(new ArrayList<>(grouped.keySet())).forEach(score -> {
            CandidateScoresBuilder candidate = grouped.get(score.applicationId());
            if (candidate != null) {
                candidate.scores.add(score);
            }
        });
        return grouped.values().stream().map(CandidateScoresBuilder::build).toList();
    }

    @Transactional
    public ScoreData save(
            long applicationId, AuthenticatedActor actor, BigDecimal score, String comment
    ) {
        authorization.requirePermission(actor, "canShortlist");
        if (score == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A numeric score is required");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "score must be between 0 and 100");
        }
        String candidate = repository.candidateName(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));
        ScoreData saved = repository.save(
                applicationId, actor.id(), score,
                comment == null || comment.isBlank() ? null : comment);
        audit.write(actor, "Scored candidate", candidate + " — " + score);
        return saved;
    }

    private static final class CandidateScoresBuilder {
        private final long applicationId;
        private final String candidateName;
        private final String candidateEmail;
        private final String status;
        private final List<ScoreData> scores = new ArrayList<>();

        private CandidateScoresBuilder(
                long applicationId, String candidateName, String candidateEmail, String status
        ) {
            this.applicationId = applicationId;
            this.candidateName = candidateName;
            this.candidateEmail = candidateEmail;
            this.status = status;
        }

        private CandidateScores build() {
            BigDecimal average = scores.isEmpty() ? null : scores.stream()
                    .map(ScoreData::score).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            return new CandidateScores(
                    applicationId, candidateName, candidateEmail, status,
                    List.copyOf(scores), average);
        }
    }

    public record CandidateScores(
            long applicationId, String candidateName, String candidateEmail,
            String status, List<ScoreData> scores, BigDecimal average
    ) {
    }
}
