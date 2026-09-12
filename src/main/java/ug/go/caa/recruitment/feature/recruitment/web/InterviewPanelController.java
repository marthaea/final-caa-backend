package ug.go.caa.recruitment.feature.recruitment.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ug.go.caa.recruitment.feature.recruitment.application.InterviewPanelService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.InterviewPanelRepository.PanelMember;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/interview-panel")
public class InterviewPanelController {

    private final InterviewPanelService service;

    public InterviewPanelController(InterviewPanelService service) {
        this.service = service;
    }

    @GetMapping("/{jobId}")
    ApiResponse<List<PanelMember>> findByJob(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long jobId
    ) {
        List<PanelMember> result = service.findByJob(jobId, AuthenticatedActor.from(jwt));
        return ApiResponse.list(result, result.size());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PanelMember> add(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AddPanelistRequest request
    ) {
        return ApiResponse.success(
                service.add(request.jobId(), request.staffId(), AuthenticatedActor.from(jwt)));
    }

    @DeleteMapping("/{id}")
    ApiResponse<Void> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @RequestParam long jobId
    ) {
        service.remove(id, jobId, AuthenticatedActor.from(jwt));
        return ApiResponse.success(null);
    }

    record AddPanelistRequest(long jobId, long staffId) {
    }
}
