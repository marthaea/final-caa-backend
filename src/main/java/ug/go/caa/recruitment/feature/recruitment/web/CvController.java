package ug.go.caa.recruitment.feature.recruitment.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.feature.recruitment.application.CvService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CvRepository.CvData;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.CvRepository.CvWrite;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/cv")
public class CvController {

    private final CvService cvs;

    public CvController(CvService cvs) {
        this.cvs = cvs;
    }

    @GetMapping
    ApiResponse<CvData> own(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(cvs.own(AuthenticatedActor.from(jwt)));
    }

    @PutMapping
    ApiResponse<CvData> save(
            @AuthenticationPrincipal Jwt jwt, @RequestBody CvRequest request
    ) {
        return ApiResponse.success(cvs.save(
                AuthenticatedActor.from(jwt),
                new CvWrite(request.personal(), request.highestLevel(), request.qualifications(),
                        request.skills(), request.experience(), request.referees(),
                        request.nextOfKin(), request.photoFile())));
    }

    @GetMapping("/by-email/{email}")
    ApiResponse<CvData> byEmail(
            @PathVariable String email, @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(cvs.byEmail(AuthenticatedActor.from(jwt), email));
    }

    record CvRequest(
            JsonNode personal, String highestLevel, JsonNode qualifications,
            JsonNode skills, JsonNode experience, JsonNode referees,
            JsonNode nextOfKin, String photoFile
    ) {
    }
}
