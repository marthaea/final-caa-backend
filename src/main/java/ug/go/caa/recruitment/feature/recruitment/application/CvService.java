package ug.go.caa.recruitment.feature.recruitment.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CvRepository;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CvRepository.CvData;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CvRepository.CvWrite;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class CvService {

    private final CvRepository repository;
    private final AuthorizationService authorization;

    public CvService(CvRepository repository, AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    public CvData own(AuthenticatedActor actor) {
        return repository.find(actor.email())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV not found"));
    }

    @Transactional
    public CvData save(AuthenticatedActor actor, CvWrite cv) {
        CvWrite merged = merge(repository.find(actor.email()).orElse(null), cv);
        return repository.save(actor.id(), actor.email().toLowerCase(), merged);
    }

    public CvData byEmail(AuthenticatedActor actor, String email) {
        authorization.requirePermission(actor, "canViewApplications");
        return repository.find(email).orElse(null);
    }

    private static CvWrite merge(CvData existing, CvWrite incoming) {
        if (existing == null) {
            return incoming;
        }
        return new CvWrite(
                incoming.personal() != null ? incoming.personal() : existing.personal(),
                firstNonBlank(incoming.highestLevel(), existing.highestLevel()),
                incoming.qualifications() != null ? incoming.qualifications() : existing.qualifications(),
                incoming.skills() != null ? incoming.skills() : existing.skills(),
                incoming.experience() != null ? incoming.experience() : existing.experience(),
                incoming.referees() != null ? incoming.referees() : existing.referees(),
                incoming.nextOfKin() != null ? incoming.nextOfKin() : existing.nextOfKin(),
                firstNonBlank(incoming.photoFile(), existing.photoFile()));
    }

    private static String firstNonBlank(String incoming, String existing) {
        return incoming != null && !incoming.isBlank() ? incoming : existing;
    }
}
